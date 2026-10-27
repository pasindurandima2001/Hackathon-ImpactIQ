package com.impactiq.rag;

import com.impactiq.config.KnowledgeProperties;
import com.impactiq.entity.DocumentChunk;
import com.impactiq.entity.DocumentVersion;
import com.impactiq.entity.KnowledgeDocument;
import com.impactiq.entity.ProjectRole;
import com.impactiq.repository.DocumentChunkRepository;
import com.impactiq.repository.DocumentVersionRepository;
import com.impactiq.repository.KnowledgeDocumentRepository;
import com.impactiq.security.CurrentUser;
import com.impactiq.service.AccessControlService;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Hybrid retrieval over one project's indexed chunks: BM25 lexical scoring plus cosine similarity of
 * Gemini embeddings, both computed in the JVM. Authorization is checked here and the database query is
 * always filtered by project, so retrieval can never cross project boundaries.
 */
@Service
public class KnowledgeRetrievalService {

    public static final String MODE_HYBRID = "HYBRID";
    public static final String MODE_LEXICAL = "LEXICAL";

    private static final double BM25_K1 = 1.2;
    private static final double BM25_B = 0.75;
    private static final double SEMANTIC_WEIGHT = 0.65;
    private static final double LEXICAL_WEIGHT = 0.35;
    private static final double COMPONENT_BOOST = 0.05;
    private static final double MIN_SEMANTIC_ONLY_SCORE = 0.55;
    private static final int MAX_QUERY_CHARS = 4000;
    private static final Set<String> STOPWORDS = Set.of(
            "the", "and", "for", "are", "but", "not", "you", "all", "any", "can", "had", "her", "was", "one", "our",
            "out", "has", "have", "this", "that", "with", "from", "they", "will", "when", "what", "which", "there",
            "their", "into", "been", "were", "then", "than", "them", "also", "is", "in", "on", "at", "to", "of",
            "an", "or", "be", "by", "it", "as", "if", "we", "do", "no", "so", "a"
    );

    private final DocumentChunkRepository chunkRepository;
    private final KnowledgeDocumentRepository documentRepository;
    private final DocumentVersionRepository versionRepository;
    private final EmbeddingService embeddingService;
    private final AccessControlService accessControl;
    private final KnowledgeProperties properties;

    public KnowledgeRetrievalService(
            DocumentChunkRepository chunkRepository,
            KnowledgeDocumentRepository documentRepository,
            DocumentVersionRepository versionRepository,
            EmbeddingService embeddingService,
            AccessControlService accessControl,
            KnowledgeProperties properties
    ) {
        this.chunkRepository = chunkRepository;
        this.documentRepository = documentRepository;
        this.versionRepository = versionRepository;
        this.embeddingService = embeddingService;
        this.accessControl = accessControl;
        this.properties = properties;
    }

    @Transactional(readOnly = true)
    public RetrievalResult retrieve(CurrentUser user, Long projectId, Long componentId, String query, int topK) {
        accessControl.requireProjectRole(user, projectId, ProjectRole.VIEWER);
        String boundedQuery = query == null ? "" : query.substring(0, Math.min(query.length(), MAX_QUERY_CHARS));
        List<DocumentChunk> candidates = chunkRepository
                .findRetrievalCandidates(projectId, componentId, Limit.of(Math.max(1, properties.getMaxCandidateChunks())))
                .stream()
                // Defence in depth: never trust a chunk whose project differs, even if the query changes later.
                .filter(chunk -> projectId.equals(chunk.getProjectId()))
                .toList();
        if (candidates.isEmpty() || boundedQuery.isBlank()) {
            return new RetrievalResult(MODE_LEXICAL, List.of());
        }

        double[] lexical = bm25(candidates, tokenize(boundedQuery));
        double[] semantic = semanticScores(candidates, boundedQuery);
        String mode = semantic == null ? MODE_LEXICAL : MODE_HYBRID;

        List<Scored> scored = new ArrayList<>(candidates.size());
        for (int index = 0; index < candidates.size(); index++) {
            DocumentChunk chunk = candidates.get(index);
            double lexicalScore = lexical[index];
            double semanticScore = semantic == null ? 0 : Math.max(0, semantic[index]);
            double score = semantic == null ? lexicalScore : SEMANTIC_WEIGHT * semanticScore + LEXICAL_WEIGHT * lexicalScore;
            boolean relevant = lexicalScore > 0 || semanticScore >= MIN_SEMANTIC_ONLY_SCORE;
            if (!relevant) {
                continue;
            }
            if (componentId != null && componentId.equals(chunk.getComponentId())) {
                score += COMPONENT_BOOST;
            }
            scored.add(new Scored(chunk, score, lexicalScore, semanticScore));
        }
        List<Scored> top = scored.stream()
                .sorted(Comparator.comparingDouble(Scored::score).reversed())
                .limit(Math.max(1, topK))
                .toList();
        return new RetrievalResult(mode, enrich(top));
    }

    private double[] semanticScores(List<DocumentChunk> candidates, String query) {
        boolean anyEmbedded = candidates.stream().anyMatch(chunk -> chunk.getEmbedding() != null);
        if (!anyEmbedded || !embeddingService.isAvailable()) {
            return null;
        }
        float[] queryVector;
        try {
            queryVector = embeddingService.embedQuery(query);
        } catch (EmbeddingService.EmbeddingUnavailableException exception) {
            return null;
        }
        double[] scores = new double[candidates.size()];
        for (int index = 0; index < candidates.size(); index++) {
            DocumentChunk chunk = candidates.get(index);
            if (chunk.getEmbedding() != null && Objects.equals(chunk.getEmbeddingModel(), embeddingService.model())) {
                scores[index] = VectorCodec.dot(queryVector, VectorCodec.decode(chunk.getEmbedding()));
            }
        }
        return scores;
    }

    /** BM25 normalised to [0, 1] by the best candidate score. */
    static double[] bm25(List<DocumentChunk> candidates, List<String> queryTerms) {
        double[] scores = new double[candidates.size()];
        if (queryTerms.isEmpty()) {
            return scores;
        }
        Set<String> terms = Set.copyOf(queryTerms);
        List<Map<String, Integer>> frequencies = new ArrayList<>(candidates.size());
        Map<String, Integer> documentFrequency = new HashMap<>();
        long totalLength = 0;
        int[] lengths = new int[candidates.size()];
        for (int index = 0; index < candidates.size(); index++) {
            List<String> tokens = tokenize(candidates.get(index).getContent());
            lengths[index] = tokens.size();
            totalLength += tokens.size();
            Map<String, Integer> counts = new HashMap<>();
            for (String token : tokens) {
                if (terms.contains(token)) {
                    counts.merge(token, 1, Integer::sum);
                }
            }
            counts.keySet().forEach(term -> documentFrequency.merge(term, 1, Integer::sum));
            frequencies.add(counts);
        }
        double averageLength = Math.max(1, (double) totalLength / candidates.size());
        int documentCount = candidates.size();
        double best = 0;
        for (int index = 0; index < candidates.size(); index++) {
            double score = 0;
            for (Map.Entry<String, Integer> entry : frequencies.get(index).entrySet()) {
                int df = documentFrequency.getOrDefault(entry.getKey(), 0);
                double idf = Math.log(1 + (documentCount - df + 0.5) / (df + 0.5));
                double tf = entry.getValue();
                score += idf * (tf * (BM25_K1 + 1)) / (tf + BM25_K1 * (1 - BM25_B + BM25_B * lengths[index] / averageLength));
            }
            scores[index] = score;
            best = Math.max(best, score);
        }
        if (best > 0) {
            for (int index = 0; index < scores.length; index++) {
                scores[index] /= best;
            }
        }
        return scores;
    }

    static List<String> tokenize(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        return Arrays.stream(text.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}_]+"))
                .filter(token -> token.length() >= 2 && !STOPWORDS.contains(token))
                .toList();
    }

    private List<RetrievedChunk> enrich(List<Scored> scored) {
        if (scored.isEmpty()) {
            return List.of();
        }
        Map<Long, KnowledgeDocument> documents = documentRepository
                .findAllById(scored.stream().map(item -> item.chunk().getDocumentId()).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(KnowledgeDocument::getId, Function.identity()));
        Map<Long, DocumentVersion> versions = versionRepository
                .findAllById(scored.stream().map(item -> item.chunk().getVersionId()).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(DocumentVersion::getId, Function.identity()));
        List<RetrievedChunk> results = new ArrayList<>(scored.size());
        for (Scored item : scored) {
            KnowledgeDocument document = documents.get(item.chunk().getDocumentId());
            DocumentVersion version = versions.get(item.chunk().getVersionId());
            if (document == null || version == null) {
                continue;
            }
            results.add(new RetrievedChunk(item.chunk(), document.getTitle(), version.getOriginalFilename(),
                    version.getVersionNumber(), round(item.score()), round(item.lexical()), round(item.semantic())));
        }
        return results;
    }

    private static double round(double value) {
        return Math.round(value * 1000.0) / 1000.0;
    }

    private record Scored(DocumentChunk chunk, double score, double lexical, double semantic) {
    }

    public record RetrievedChunk(
            DocumentChunk chunk,
            String documentTitle,
            String filename,
            int versionNumber,
            double score,
            double lexicalScore,
            double semanticScore
    ) {
    }

    public record RetrievalResult(String mode, List<RetrievedChunk> chunks) {
    }
}
