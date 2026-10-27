package com.impactiq.service;

import com.impactiq.agent.AgentException;
import com.impactiq.agent.ComponentDiscoveryAgent;
import com.impactiq.config.ComponentDiscoveryProperties;
import com.impactiq.dto.ProjectDtos.ComponentDiscoveryResponse;
import com.impactiq.dto.ProjectDtos.ComponentResponse;
import com.impactiq.entity.DocumentChunk;
import com.impactiq.entity.DocumentStatus;
import com.impactiq.entity.KnowledgeDocument;
import com.impactiq.entity.Project;
import com.impactiq.entity.ProjectComponent;
import com.impactiq.entity.ProjectRole;
import com.impactiq.exception.ApiException;
import com.impactiq.rag.DocumentIndexedEvent;
import com.impactiq.repository.DocumentChunkRepository;
import com.impactiq.repository.DocumentVersionRepository;
import com.impactiq.repository.KnowledgeDocumentRepository;
import com.impactiq.repository.ProjectComponentRepository;
import com.impactiq.repository.ProjectRepository;
import com.impactiq.security.CurrentUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.event.EventListener;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Finds software components in a project's indexed knowledge with {@link ComponentDiscoveryAgent} and adds the
 * new ones. Runs automatically after a document is indexed (best-effort, in the background) and on demand from
 * the knowledge page. The model call happens outside any transaction; only validated names are saved.
 */
@Service
public class ComponentDiscoveryService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ComponentDiscoveryService.class);
    private static final int DESCRIPTION_MAX = 1000;

    private final ComponentDiscoveryAgent agent;
    private final ComponentDiscoveryProperties properties;
    private final ProjectRepository projectRepository;
    private final ProjectComponentRepository componentRepository;
    private final KnowledgeDocumentRepository documentRepository;
    private final DocumentVersionRepository versionRepository;
    private final DocumentChunkRepository chunkRepository;
    private final AccessControlService accessControl;
    private final AuditService auditService;
    private final TransactionTemplate transactionTemplate;
    private final ThreadPoolTaskExecutor executor;
    /** Serialises runs per project on this instance; the unique (project_id, name) key covers other instances. */
    private final ConcurrentHashMap<Long, Object> projectLocks = new ConcurrentHashMap<>();

    public ComponentDiscoveryService(
            ComponentDiscoveryAgent agent,
            ComponentDiscoveryProperties properties,
            ProjectRepository projectRepository,
            ProjectComponentRepository componentRepository,
            KnowledgeDocumentRepository documentRepository,
            DocumentVersionRepository versionRepository,
            DocumentChunkRepository chunkRepository,
            AccessControlService accessControl,
            AuditService auditService,
            TransactionTemplate transactionTemplate,
            @Qualifier("componentDiscoveryExecutor") ThreadPoolTaskExecutor executor
    ) {
        this.agent = agent;
        this.properties = properties;
        this.projectRepository = projectRepository;
        this.componentRepository = componentRepository;
        this.documentRepository = documentRepository;
        this.versionRepository = versionRepository;
        this.chunkRepository = chunkRepository;
        this.accessControl = accessControl;
        this.auditService = auditService;
        this.transactionTemplate = transactionTemplate;
        this.executor = executor;
    }

    @EventListener
    public void onDocumentIndexed(DocumentIndexedEvent event) {
        if (!properties.isAutoDiscover()) {
            return;
        }
        try {
            executor.execute(() -> discoverFromDocument(event.documentId()));
        } catch (TaskRejectedException exception) {
            LOGGER.warn("Component discovery queue is full; skipped documentId={}", event.documentId());
        }
    }

    /** Background run for one newly indexed document. Failures are logged and never surface to the user. */
    void discoverFromDocument(Long documentId) {
        try {
            documentRepository.findById(documentId)
                    .filter(document -> document.getActiveVersionNumber() != null)
                    .ifPresent(document -> run(document.getProjectId(), List.of(document), null));
        } catch (AgentException exception) {
            LOGGER.info("Automatic component discovery skipped for documentId={}: category={}", documentId, exception.category());
        } catch (RuntimeException exception) {
            LOGGER.warn("Automatic component discovery failed for documentId={}: {}", documentId, exception.getClass().getSimpleName());
        }
    }

    /** On-demand run over the project's most recently updated indexed documents. Requires EDITOR. */
    public ComponentDiscoveryResponse discoverForProject(CurrentUser user, Long projectId) {
        accessControl.requireAdmin(user);
        accessControl.requireProjectRole(user, projectId, ProjectRole.EDITOR);
        List<KnowledgeDocument> documents = documentRepository.findByProjectIdOrderByUpdatedAtDesc(projectId).stream()
                .filter(document -> document.getStatus() == DocumentStatus.INDEXED && document.getActiveVersionNumber() != null)
                .limit(Math.max(1, properties.getMaxDocuments()))
                .toList();
        if (documents.isEmpty()) {
            throw ApiException.badRequest("Upload and index at least one document before detecting components");
        }
        try {
            List<ComponentResponse> added = run(projectId, documents, user);
            String message = added.isEmpty()
                    ? "No new components were found in the indexed documents."
                    : "Added " + added.size() + " component(s) detected by AI.";
            return new ComponentDiscoveryResponse(added, documents.size(), message);
        } catch (AgentException exception) {
            throw ApiException.unavailable(switch (exception.category()) {
                case MISSING_API_KEY -> "AI component detection is not configured on the server (missing Gemini API key).";
                case RATE_LIMITED -> "The AI model is rate limited. Try again in a minute.";
                case MODEL_TIMEOUT, RUN_TIMEOUT -> "AI component detection timed out. Try again.";
                default -> "AI component detection is temporarily unavailable. Try again.";
            });
        }
    }

    private List<ComponentResponse> run(Long projectId, List<KnowledgeDocument> documents, CurrentUser actor) {
        synchronized (projectLocks.computeIfAbsent(projectId, id -> new Object())) {
            Optional<Project> project = projectRepository.findById(projectId);
            if (project.isEmpty()) {
                return List.of();
            }
            List<String> existing = componentRepository.findByProjectIdOrderByNameAsc(projectId).stream()
                    .map(ProjectComponent::getName).toList();
            if (existing.size() >= properties.getMaxPerProject()) {
                return List.of();
            }
            List<ComponentDiscoveryAgent.Source> sources = sources(documents);
            if (sources.isEmpty()) {
                return List.of();
            }
            List<ComponentDiscoveryAgent.Proposal> proposals = agent.discover(
                    new ComponentDiscoveryAgent.Request(project.get().getName(), existing, sources),
                    Instant.now().plusMillis(properties.getTimeoutMs()));
            int room = Math.min(properties.getMaxPerRun(), properties.getMaxPerProject() - existing.size());
            List<ComponentResponse> added = new ArrayList<>();
            for (ComponentDiscoveryAgent.Proposal proposal : proposals.stream().limit(Math.max(0, room)).toList()) {
                String sourceTitles = sources.stream().filter(source -> proposal.sourceIds().contains(source.id()))
                        .map(ComponentDiscoveryAgent.Source::title).distinct().reduce((a, b) -> a + ", " + b).orElse("project documents");
                save(projectId, proposal, sourceTitles, actor).ifPresent(added::add);
            }
            return added;
        }
    }

    /** One transaction per component, so a concurrent insert of the same name only skips that one. */
    private Optional<ComponentResponse> save(Long projectId, ComponentDiscoveryAgent.Proposal proposal, String sourceTitles, CurrentUser actor) {
        try {
            return Optional.ofNullable(transactionTemplate.execute(status -> {
                if (!projectRepository.existsById(projectId)
                        || componentRepository.existsByProjectIdAndNameIgnoreCase(projectId, proposal.name())) {
                    return null;
                }
                ProjectComponent component = new ProjectComponent();
                component.setProjectId(projectId);
                component.setName(proposal.name());
                String description = (proposal.description().isEmpty() ? "" : proposal.description() + " ")
                        + "(Detected by AI from " + sourceTitles + ")";
                component.setDescription(description.length() > DESCRIPTION_MAX ? description.substring(0, DESCRIPTION_MAX) : description);
                ProjectComponent saved = componentRepository.saveAndFlush(component);
                auditService.record(actor, "COMPONENT_DISCOVERED", "COMPONENT", saved.getId(), projectId,
                        saved.getName() + " from " + sourceTitles);
                return new ComponentResponse(saved.getId(), projectId, saved.getName(), saved.getDescription());
            }));
        } catch (DataIntegrityViolationException exception) {
            return Optional.empty();
        }
    }

    /** Active-version text of each document in chunk order, skipping chunks flagged as instruction-like. */
    private List<ComponentDiscoveryAgent.Source> sources(List<KnowledgeDocument> documents) {
        int budget = Math.max(500, properties.getExcerptChars() / documents.size());
        List<ComponentDiscoveryAgent.Source> sources = new ArrayList<>();
        for (KnowledgeDocument document : documents) {
            Optional<Long> versionId = versionRepository.findByDocumentIdAndVersionNumber(document.getId(), document.getActiveVersionNumber())
                    .map(version -> version.getId());
            if (versionId.isEmpty()) {
                continue;
            }
            StringBuilder text = new StringBuilder();
            for (DocumentChunk chunk : chunkRepository.findByVersionIdOrderByChunkIndexAsc(versionId.get()).stream()
                    .sorted(Comparator.comparingInt(DocumentChunk::getChunkIndex)).toList()) {
                if (chunk.isSuspicious() || text.length() >= budget) {
                    continue;
                }
                text.append(chunk.getContent(), 0, Math.min(chunk.getContent().length(), budget - text.length())).append('\n');
            }
            if (!text.isEmpty()) {
                sources.add(new ComponentDiscoveryAgent.Source("S" + (sources.size() + 1), document.getTitle(), text.toString()));
            }
        }
        return sources;
    }
}
