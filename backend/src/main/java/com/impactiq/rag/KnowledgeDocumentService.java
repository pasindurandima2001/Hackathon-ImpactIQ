package com.impactiq.rag;

import com.impactiq.dto.KnowledgeDtos.DocumentDetailResponse;
import com.impactiq.dto.KnowledgeDtos.DocumentResponse;
import com.impactiq.dto.KnowledgeDtos.SearchResponse;
import com.impactiq.dto.KnowledgeDtos.SearchResultResponse;
import com.impactiq.dto.KnowledgeDtos.VersionResponse;
import com.impactiq.entity.AppUser;
import com.impactiq.entity.DocumentStatus;
import com.impactiq.entity.DocumentVersion;
import com.impactiq.entity.KnowledgeDocument;
import com.impactiq.entity.ProjectComponent;
import com.impactiq.entity.ProjectRole;
import com.impactiq.exception.ApiException;
import com.impactiq.repository.AppUserRepository;
import com.impactiq.repository.DocumentChunkRepository;
import com.impactiq.repository.DocumentVersionRepository;
import com.impactiq.repository.KnowledgeDocumentRepository;
import com.impactiq.repository.ProjectComponentRepository;
import com.impactiq.security.CurrentUser;
import com.impactiq.service.AccessControlService;
import com.impactiq.service.AuditService;
import com.impactiq.service.ProjectService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Transactional
public class KnowledgeDocumentService {

    private static final int MAX_TITLE_LENGTH = 255;
    private static final int EXCERPT_CHARS = 600;
    private static final int DEFAULT_TOP_K = 8;

    private final KnowledgeDocumentRepository documentRepository;
    private final DocumentVersionRepository versionRepository;
    private final DocumentChunkRepository chunkRepository;
    private final ProjectComponentRepository componentRepository;
    private final AppUserRepository userRepository;
    private final FileValidationService fileValidationService;
    private final DocumentStorageService storageService;
    private final DocumentProcessingService processingService;
    private final KnowledgeRetrievalService retrievalService;
    private final AccessControlService accessControl;
    private final ProjectService projectService;
    private final AuditService auditService;

    public KnowledgeDocumentService(
            KnowledgeDocumentRepository documentRepository,
            DocumentVersionRepository versionRepository,
            DocumentChunkRepository chunkRepository,
            ProjectComponentRepository componentRepository,
            AppUserRepository userRepository,
            FileValidationService fileValidationService,
            DocumentStorageService storageService,
            DocumentProcessingService processingService,
            KnowledgeRetrievalService retrievalService,
            AccessControlService accessControl,
            ProjectService projectService,
            AuditService auditService
    ) {
        this.documentRepository = documentRepository;
        this.versionRepository = versionRepository;
        this.chunkRepository = chunkRepository;
        this.componentRepository = componentRepository;
        this.userRepository = userRepository;
        this.fileValidationService = fileValidationService;
        this.storageService = storageService;
        this.processingService = processingService;
        this.retrievalService = retrievalService;
        this.accessControl = accessControl;
        this.projectService = projectService;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<DocumentResponse> list(CurrentUser user, Long projectId, Long componentId) {
        accessControl.requireAdmin(user);
        accessControl.requireProjectRole(user, projectId, ProjectRole.VIEWER);
        List<KnowledgeDocument> documents = documentRepository.findByProjectIdOrderByUpdatedAtDesc(projectId).stream()
                .filter(document -> componentId == null || componentId.equals(document.getComponentId()))
                .toList();
        return toResponses(documents);
    }

    @Transactional(readOnly = true)
    public DocumentDetailResponse get(CurrentUser user, Long projectId, Long documentId) {
        accessControl.requireAdmin(user);
        accessControl.requireProjectRole(user, projectId, ProjectRole.VIEWER);
        KnowledgeDocument document = requireDocument(projectId, documentId);
        List<DocumentVersion> versions = versionRepository.findByDocumentIdOrderByVersionNumberDesc(documentId);
        Map<Long, String> usernames = usernames(versions.stream().map(DocumentVersion::getUploadedBy).toList());
        return new DocumentDetailResponse(toResponses(List.of(document)).get(0),
                versions.stream().map(version -> toResponse(document, version, usernames)).toList());
    }

    public DocumentResponse upload(CurrentUser user, Long projectId, Long componentId, String title, MultipartFile file) {
        accessControl.requireAdmin(user);
        accessControl.requireProjectRole(user, projectId, ProjectRole.EDITOR);
        if (componentId != null) {
            projectService.requireComponent(projectId, componentId);
        }
        ValidatedFile validated = fileValidationService.validate(file);
        KnowledgeDocument document = new KnowledgeDocument();
        document.setProjectId(projectId);
        document.setComponentId(componentId);
        document.setTitle(title == null || title.isBlank() ? validated.filename() : truncate(title.trim(), MAX_TITLE_LENGTH));
        document.setSourceType(validated.extension());
        document.setStatus(DocumentStatus.PENDING);
        document.setLatestVersionNumber(1);
        document.setUploadedBy(user.id());
        KnowledgeDocument saved = documentRepository.saveAndFlush(document);
        DocumentVersion version = createVersion(user, saved, validated, 1);
        auditService.record(user, "DOCUMENT_UPLOADED", "DOCUMENT", saved.getId(), projectId,
                validated.filename() + " v1 sha256=" + validated.sha256().substring(0, 12));
        processingService.submitAfterCommit(version.getId());
        return toResponses(List.of(saved)).get(0);
    }

    public DocumentResponse uploadVersion(CurrentUser user, Long projectId, Long documentId, MultipartFile file) {
        accessControl.requireAdmin(user);
        accessControl.requireProjectRole(user, projectId, ProjectRole.EDITOR);
        KnowledgeDocument document = requireDocument(projectId, documentId);
        requireNotProcessing(documentId);
        ValidatedFile validated = fileValidationService.validate(file);
        int next = document.getLatestVersionNumber() + 1;
        document.setLatestVersionNumber(next);
        document.setSourceType(validated.extension());
        if (document.getActiveVersionNumber() == null) {
            document.setStatus(DocumentStatus.PENDING);
        }
        DocumentVersion version = createVersion(user, document, validated, next);
        auditService.record(user, "DOCUMENT_VERSION_UPLOADED", "DOCUMENT", documentId, projectId,
                validated.filename() + " v" + next + " sha256=" + validated.sha256().substring(0, 12));
        processingService.submitAfterCommit(version.getId());
        return toResponses(List.of(document)).get(0);
    }

    public DocumentResponse reprocess(CurrentUser user, Long projectId, Long documentId) {
        accessControl.requireAdmin(user);
        accessControl.requireProjectRole(user, projectId, ProjectRole.EDITOR);
        KnowledgeDocument document = requireDocument(projectId, documentId);
        requireNotProcessing(documentId);
        DocumentVersion latest = versionRepository.findByDocumentIdAndVersionNumber(documentId, document.getLatestVersionNumber())
                .orElseThrow(() -> ApiException.notFound("Document version not found"));
        if (latest.isFileDeleted()) {
            throw ApiException.conflict("The stored file for this version was removed by the retention policy");
        }
        latest.setStatus(DocumentStatus.PENDING);
        latest.setErrorMessage(null);
        auditService.record(user, "DOCUMENT_REPROCESS", "DOCUMENT", documentId, projectId, "v" + latest.getVersionNumber());
        processingService.submitAfterCommit(latest.getId());
        return toResponses(List.of(document)).get(0);
    }

    /** OWNERs may delete any document; EDITORs only documents they uploaded. Removes files, versions and indexed chunks. */
    public void delete(CurrentUser user, Long projectId, Long documentId) {
        accessControl.requireAdmin(user);
        ProjectRole role = accessControl.requireProjectRole(user, projectId, ProjectRole.EDITOR);
        KnowledgeDocument document = requireDocument(projectId, documentId);
        if (role != ProjectRole.OWNER && !Objects.equals(document.getUploadedBy(), user.id())) {
            throw ApiException.forbidden("Only project owners can delete documents uploaded by others");
        }
        List<DocumentVersion> versions = versionRepository.findByDocumentIdOrderByVersionNumberDesc(documentId);
        int removedChunks = chunkRepository.deleteByDocumentId(documentId);
        versionRepository.deleteByDocumentId(documentId);
        documentRepository.delete(document);
        // Files are removed only after the database delete commits, so a rollback never loses originals.
        List<String> storedNames = versions.stream().map(DocumentVersion::getStoredFilename).toList();
        afterCommit(() -> storedNames.forEach(name -> storageService.delete(projectId, name)));
        auditService.record(user, "DOCUMENT_DELETED", "DOCUMENT", documentId, projectId,
                document.getTitle() + " versions=" + versions.size() + " chunks=" + removedChunks);
    }

    @Transactional(readOnly = true)
    public SearchResponse search(CurrentUser user, Long projectId, Long componentId, String query, Integer topK) {
        accessControl.requireAdmin(user);
        if (componentId != null) {
            projectService.requireComponent(projectId, componentId);
        }
        KnowledgeRetrievalService.RetrievalResult result = retrievalService.retrieve(
                user, projectId, componentId, query, topK == null ? DEFAULT_TOP_K : topK);
        return new SearchResponse(result.mode(), result.chunks().stream().map(item -> new SearchResultResponse(
                item.chunk().getId(), item.chunk().getDocumentId(), item.documentTitle(), item.filename(), item.versionNumber(),
                item.chunk().getPageNumber(), item.chunk().getStartLine(), item.chunk().getEndLine(),
                item.score(), item.lexicalScore(), item.semanticScore(),
                truncate(item.chunk().getContent(), EXCERPT_CHARS), item.chunk().isSuspicious())).toList());
    }

    private DocumentVersion createVersion(CurrentUser user, KnowledgeDocument document, ValidatedFile file, int number) {
        DocumentVersion version = new DocumentVersion();
        version.setDocumentId(document.getId());
        version.setProjectId(document.getProjectId());
        version.setVersionNumber(number);
        version.setOriginalFilename(file.filename());
        version.setContentType(file.contentType());
        version.setSizeBytes(file.bytes().length);
        version.setSha256(file.sha256());
        version.setStatus(DocumentStatus.PENDING);
        version.setUploadedBy(user.id());
        version.setStoredFilename(storageService.store(document.getProjectId(), file.bytes()));
        return versionRepository.saveAndFlush(version);
    }

    private void requireNotProcessing(Long documentId) {
        boolean busy = versionRepository.findByDocumentIdOrderByVersionNumberDesc(documentId).stream()
                .anyMatch(version -> version.getStatus() == DocumentStatus.PENDING || version.getStatus() == DocumentStatus.PROCESSING);
        if (busy) {
            throw ApiException.conflict("This document is still being processed. Try again when it finishes.");
        }
    }

    private KnowledgeDocument requireDocument(Long projectId, Long documentId) {
        return documentRepository.findByIdAndProjectId(documentId, projectId)
                .orElseThrow(() -> ApiException.notFound("Document " + documentId + " was not found"));
    }

    private List<DocumentResponse> toResponses(List<KnowledgeDocument> documents) {
        Set<Long> componentIds = documents.stream().map(KnowledgeDocument::getComponentId).filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, String> componentNames = componentRepository.findByIdIn(componentIds).stream()
                .collect(Collectors.toMap(ProjectComponent::getId, ProjectComponent::getName));
        Set<Long> userIds = new HashSet<>();
        documents.forEach(document -> userIds.add(document.getUploadedBy()));
        Map<Long, String> usernames = usernames(userIds);
        return documents.stream().map(document -> {
            VersionResponse latest = versionRepository
                    .findByDocumentIdAndVersionNumber(document.getId(), document.getLatestVersionNumber())
                    .map(version -> toResponse(document, version, usernames))
                    .orElse(null);
            return new DocumentResponse(document.getId(), document.getProjectId(), document.getComponentId(),
                    componentNames.get(document.getComponentId()), document.getTitle(), document.getSourceType(),
                    document.getStatus(), document.getActiveVersionNumber(), document.getLatestVersionNumber(),
                    document.getLastError(), usernames.get(document.getUploadedBy()), document.getCreatedAt(),
                    document.getUpdatedAt(), latest);
        }).toList();
    }

    private VersionResponse toResponse(KnowledgeDocument document, DocumentVersion version, Map<Long, String> usernames) {
        return new VersionResponse(version.getId(), version.getVersionNumber(), version.getOriginalFilename(),
                version.getSizeBytes(), version.getSha256(), version.getStatus(), version.getErrorMessage(),
                version.getChunkCount(), version.getPageCount(), version.getEmbeddingStatus(), version.getRedactionCount(),
                version.isFileDeleted(), Objects.equals(document.getActiveVersionNumber(), version.getVersionNumber()),
                usernames.getOrDefault(version.getUploadedBy(), null), version.getCreatedAt(), version.getProcessedAt());
    }

    private Map<Long, String> usernames(Collection<Long> ids) {
        Set<Long> nonNull = ids.stream().filter(Objects::nonNull).collect(Collectors.toSet());
        if (nonNull.isEmpty()) {
            return Map.of();
        }
        return userRepository.findAllById(nonNull).stream()
                .collect(Collectors.toMap(AppUser::getId, AppUser::getUsername, (left, right) -> left));
    }

    private void afterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
    }

    private static String truncate(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }
}
