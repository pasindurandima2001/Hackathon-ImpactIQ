package com.impactiq.rag;

import com.google.genai.types.ContentEmbedding;
import com.google.genai.types.EmbedContentConfig;
import com.google.genai.types.EmbedContentResponse;
import com.impactiq.config.KnowledgeProperties;
import com.impactiq.service.GeminiClientFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Gemini embeddings via the existing google-genai SDK ({@code client.models.embedContent}).
 * Callers treat {@link EmbeddingUnavailableException} as "fall back to lexical retrieval".
 */
@Service
public class EmbeddingService {

    private static final Logger LOGGER = LoggerFactory.getLogger(EmbeddingService.class);
    private static final String TASK_DOCUMENT = "RETRIEVAL_DOCUMENT";
    private static final String TASK_QUERY = "RETRIEVAL_QUERY";

    private final GeminiClientFactory clientFactory;
    private final KnowledgeProperties properties;

    public EmbeddingService(GeminiClientFactory clientFactory, KnowledgeProperties properties) {
        this.clientFactory = clientFactory;
        this.properties = properties;
    }

    public boolean isAvailable() {
        return properties.isEmbeddingsEnabled() && clientFactory.isConfigured();
    }

    public String model() {
        return properties.getEmbeddingModel();
    }

    public List<float[]> embedDocuments(List<String> texts) {
        List<float[]> vectors = new ArrayList<>(texts.size());
        int batchSize = Math.max(1, properties.getEmbeddingBatchSize());
        for (int start = 0; start < texts.size(); start += batchSize) {
            List<String> batch = texts.subList(start, Math.min(texts.size(), start + batchSize));
            vectors.addAll(embed(batch, TASK_DOCUMENT));
        }
        return vectors;
    }

    public float[] embedQuery(String text) {
        return embed(List.of(text), TASK_QUERY).get(0);
    }

    private List<float[]> embed(List<String> texts, String taskType) {
        if (!isAvailable()) {
            throw new EmbeddingUnavailableException("Embeddings are not configured");
        }
        try {
            EmbedContentConfig config = EmbedContentConfig.builder()
                    .taskType(taskType)
                    .outputDimensionality(properties.getEmbeddingDimensions())
                    .build();
            EmbedContentResponse response = clientFactory.client().models
                    .embedContent(properties.getEmbeddingModel(), texts, config);
            List<ContentEmbedding> embeddings = response.embeddings().orElse(List.of());
            if (embeddings.size() != texts.size()) {
                throw new EmbeddingUnavailableException("Embedding response size did not match the request");
            }
            List<float[]> vectors = new ArrayList<>(embeddings.size());
            for (ContentEmbedding embedding : embeddings) {
                float[] vector = VectorCodec.normalize(embedding.values().orElse(List.of()));
                if (vector == null) {
                    throw new EmbeddingUnavailableException("Embedding response contained an empty vector");
                }
                vectors.add(vector);
            }
            return vectors;
        } catch (EmbeddingUnavailableException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            LOGGER.warn("Embedding request failed: category={}", exception.getClass().getSimpleName());
            throw new EmbeddingUnavailableException("Embedding service is unavailable");
        }
    }

    public static class EmbeddingUnavailableException extends RuntimeException {
        public EmbeddingUnavailableException(String message) {
            super(message);
        }
    }
}
