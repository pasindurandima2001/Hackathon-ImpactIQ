package com.impactiq.rag;

import com.impactiq.entity.DocumentChunk;
import com.impactiq.entity.DocumentStatus;
import com.impactiq.entity.DocumentVersion;
import com.impactiq.entity.KnowledgeDocument;
import com.impactiq.repository.DocumentChunkRepository;
import com.impactiq.repository.DocumentVersionRepository;
import com.impactiq.repository.KnowledgeDocumentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Background pipeline: read stored file → validate → extract → redact → chunk → embed → index.
 * New chunks replace the previous version's chunks atomically; if processing fails, the previously
 * active version stays searchable.
 */
@Service
public class DocumentProcessingService {

    private static final Logger LOGGER = LoggerFactory.getLogger(DocumentProcessingService.class);
    private static final int MAX_ERROR_LENGTH = 1000;
    public static final String EMBEDDED = "EMBEDDED";
    public static final String LEXICAL_ONLY = "LEXICAL_ONLY";

    private final KnowledgeDocumentRepository documentRepository;
    private final DocumentVersionRepository versionRepository;
    private final DocumentChunkRepository chunkRepository;
    private final DocumentStorageService storageService;
    private final FileValidationService fileValidationService;
    private final TextExtractionService extractionService;
    private final ChunkingService chunkingService;
    private final SecretRedactor secretRedactor;
    private final PromptInjectionDetector injectionDetector;
    private final EmbeddingService embeddingService;
    private final TransactionTemplate transactionTemplate;
    private final ThreadPoolTaskExecutor executor;
    private final ApplicationEventPublisher eventPublisher;

    public DocumentProcessingService(
            KnowledgeDocumentRepository documentRepository,
            DocumentVersionRepository versionRepository,
            DocumentChunkRepository chunkRepository,
            DocumentStorageService storageService,
            FileValidationService fileValidationService,
            TextExtractionService extractionService,
            ChunkingService chunkingService,
            SecretRedactor secretRedactor,
            PromptInjectionDetector injectionDetector,
            EmbeddingService embeddingService,
            TransactionTemplate transactionTemplate,
            @Qualifier("documentExecutor") ThreadPoolTaskExecutor executor,
            ApplicationEventPublisher eventPublisher
    ) {
        this.documentRepository = documentRepository;
        this.versionRepository = versionRepository;
        this.chunkRepository = chunkRepository;
        this.storageService = storageService;
        this.fileValidationService = fileValidationService;
        this.extractionService = extractionService;
        this.chunkingService = chunkingService;
        this.secretRedactor = secretRedactor;
        this.injectionDetector = injectionDetector;
        this.embeddingService = embeddingService;
        this.transactionTemplate = transactionTemplate;
        this.executor = executor;
        this.eventPublisher = eventPublisher;
    }

    /** Queues processing once the surrounding transaction commits, so the worker sees the saved rows. */
    public void submitAfterCommit(Long versionId) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    submit(versionId);
                }
            });
        } else {
            submit(versionId);
        }
    }

    public void submit(Long versionId) {
        try {
            executor.execute(() -> process(versionId));
        } catch (TaskRejectedException exception) {
            markFailed(versionId, "Processing queue is full. Use Reprocess to try again.");
        }
    }

    public void process(Long versionId) {
        DocumentVersion version = transactionTemplate.execute(status -> {
            DocumentVersion current = versionRepository.findById(versionId).orElse(null);
            if (current == null) {
                return null;
            }
            current.setStatus(DocumentStatus.PROCESSING);
            current.setErrorMessage(null);
            documentRepository.findById(current.getDocumentId()).ifPresent(document -> {
                if (document.getActiveVersionNumber() == null) {
                    document.setStatus(DocumentStatus.PROCESSING);
                }
            });
            return current;
        });
        if (version == null) {
            return;
        }
        try {
            KnowledgeDocument document = documentRepository.findById(version.getDocumentId())
                    .orElseThrow(() -> new IllegalStateException("Document no longer exists"));
            byte[] bytes = storageService.read(version.getProjectId(), version.getStoredFilename());
            ValidatedFile file = fileValidationService.validate(bytes, version.getOriginalFilename());
            if (!file.sha256().equals(version.getSha256())) {
                throw new TextExtractionService.ExtractionException("Stored file failed its integrity check");
            }
            TextExtractionService.ExtractedText extracted = extractionService.extract(file);

            int redactions = 0;
            List<TextExtractionService.ExtractedText.Page> redactedPages = new ArrayList<>();
            for (TextExtractionService.ExtractedText.Page page : extracted.pages()) {
                SecretRedactor.RedactionResult result = secretRedactor.redact(page.text());
                redactions += result.redactionCount();
                redactedPages.add(new TextExtractionService.ExtractedText.Page(page.pageNumber(), result.text()));
            }
            List<ChunkingService.TextChunk> textChunks = chunkingService.chunk(redactedPages);
            if (textChunks.isEmpty()) {
                throw new TextExtractionService.ExtractionException("No indexable text was found");
            }

            List<float[]> vectors = null;
            String embeddingStatus = LEXICAL_ONLY;
            if (embeddingService.isAvailable()) {
                try {
                    vectors = embeddingService.embedDocuments(textChunks.stream().map(ChunkingService.TextChunk::content).toList());
                    embeddingStatus = EMBEDDED;
                } catch (EmbeddingService.EmbeddingUnavailableException exception) {
                    LOGGER.warn("Embeddings unavailable for versionId={}; indexing lexically only", versionId);
                }
            }

            List<DocumentChunk> chunks = new ArrayList<>(textChunks.size());
            for (int index = 0; index < textChunks.size(); index++) {
                ChunkingService.TextChunk textChunk = textChunks.get(index);
                DocumentChunk chunk = new DocumentChunk();
                chunk.setDocumentId(document.getId());
                chunk.setVersionId(version.getId());
                chunk.setProjectId(document.getProjectId());
                chunk.setComponentId(document.getComponentId());
                chunk.setChunkIndex(index);
                chunk.setContent(textChunk.content());
                chunk.setStartLine(textChunk.startLine());
                chunk.setEndLine(textChunk.endLine());
                chunk.setPageNumber(textChunk.pageNumber());
                chunk.setSuspicious(injectionDetector.isSuspicious(textChunk.content()));
                if (vectors != null) {
                    chunk.setEmbedding(VectorCodec.encode(vectors.get(index)));
                    chunk.setEmbeddingModel(embeddingService.model());
                }
                chunks.add(chunk);
            }
            int redactionCount = redactions;
            String finalEmbeddingStatus = embeddingStatus;
            Boolean indexed = transactionTemplate.execute(status -> commitIndex(version.getId(), chunks,
                    extracted.pageCount(), redactionCount, finalEmbeddingStatus));
            if (Boolean.TRUE.equals(indexed)) {
                // Listeners run after the commit; they must not throw back into the pipeline.
                eventPublisher.publishEvent(new DocumentIndexedEvent(document.getId(), document.getProjectId()));
            }
        } catch (TextExtractionService.ExtractionException exception) {
            markFailed(versionId, exception.getMessage());
        } catch (com.impactiq.exception.ApiException exception) {
            markFailed(versionId, exception.getMessage());
        } catch (RuntimeException exception) {
            LOGGER.warn("Document processing failed for versionId={} category={}", versionId, exception.getClass().getSimpleName());
            markFailed(versionId, "Processing failed unexpectedly. Use Reprocess to try again.");
        }
    }

    /** @return true when the version became the active, searchable one */
    private boolean commitIndex(Long versionId, List<DocumentChunk> chunks, Integer pageCount, int redactions, String embeddingStatus) {
        DocumentVersion version = versionRepository.findById(versionId)
                .orElseThrow(() -> new IllegalStateException("Version was deleted during processing"));
        KnowledgeDocument document = documentRepository.findById(version.getDocumentId())
                .orElseThrow(() -> new IllegalStateException("Document was deleted during processing"));
        if (document.getActiveVersionNumber() != null && document.getActiveVersionNumber() > version.getVersionNumber()) {
            // A newer version is already active; keep this version's metadata but do not index stale content.
            version.setStatus(DocumentStatus.FAILED);
            version.setErrorMessage("Superseded by version " + document.getActiveVersionNumber() + " before indexing completed");
            return false;
        }
        chunkRepository.deleteByVersionId(versionId);
        chunkRepository.saveAll(chunks);
        chunkRepository.deleteOtherVersions(document.getId(), versionId);

        version.setStatus(DocumentStatus.INDEXED);
        version.setChunkCount(chunks.size());
        version.setPageCount(pageCount);
        version.setRedactionCount(redactions);
        version.setEmbeddingStatus(embeddingStatus);
        version.setProcessedAt(Instant.now());

        document.setStatus(DocumentStatus.INDEXED);
        document.setActiveVersionNumber(version.getVersionNumber());
        document.setLastError(null);
        return true;
    }

    private void markFailed(Long versionId, String message) {
        String safeMessage = message == null ? "Processing failed" : message.substring(0, Math.min(message.length(), MAX_ERROR_LENGTH));
        transactionTemplate.executeWithoutResult(status -> versionRepository.findById(versionId).ifPresent(version -> {
            version.setStatus(DocumentStatus.FAILED);
            version.setErrorMessage(safeMessage);
            version.setProcessedAt(Instant.now());
            documentRepository.findById(version.getDocumentId()).ifPresent(document -> {
                document.setLastError("Version " + version.getVersionNumber() + ": " + safeMessage);
                document.setStatus(document.getActiveVersionNumber() == null ? DocumentStatus.FAILED : DocumentStatus.INDEXED);
            });
        }));
    }

    /** Re-queues versions left PENDING or PROCESSING by a previous shutdown or crash. Processing is idempotent. */
    @EventListener(ApplicationReadyEvent.class)
    public void recoverInterrupted() {
        List<DocumentVersion> interrupted = versionRepository.findByStatusIn(Set.of(DocumentStatus.PENDING, DocumentStatus.PROCESSING));
        if (!interrupted.isEmpty()) {
            LOGGER.info("Re-queuing {} interrupted document version(s)", interrupted.size());
        }
        interrupted.forEach(version -> submit(version.getId()));
    }
}
