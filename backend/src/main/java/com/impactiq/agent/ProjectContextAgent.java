package com.impactiq.agent;

import com.impactiq.agent.AgentContracts.EvidenceItem;
import com.impactiq.agent.AgentContracts.ProjectContextOutput;
import com.impactiq.agent.AgentContracts.SourceRef;
import com.impactiq.config.InvestigationProperties;
import com.impactiq.entity.Project;
import com.impactiq.entity.ProjectComponent;
import com.impactiq.exception.ApiException;
import com.impactiq.rag.KnowledgeRetrievalService;
import com.impactiq.repository.ProjectComponentRepository;
import com.impactiq.repository.ProjectRepository;
import com.impactiq.security.CurrentUser;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Retrieves authorised project and component context. Deliberately deterministic (no LLM): retrieval is
 * scoped to the defect's own project, re-checks the triggering user's access, and returns cited chunks.
 */
@Component
public class ProjectContextAgent implements InvestigationAgent<ProjectContextAgent.Input, ProjectContextOutput> {

    private final KnowledgeRetrievalService retrievalService;
    private final ProjectRepository projectRepository;
    private final ProjectComponentRepository componentRepository;
    private final InvestigationProperties properties;

    public ProjectContextAgent(
            KnowledgeRetrievalService retrievalService,
            ProjectRepository projectRepository,
            ProjectComponentRepository componentRepository,
            InvestigationProperties properties
    ) {
        this.retrievalService = retrievalService;
        this.projectRepository = projectRepository;
        this.componentRepository = componentRepository;
        this.properties = properties;
    }

    public record Input(Long projectId, Long componentId, CurrentUser user, String query) {
    }

    @Override
    public String name() {
        return "project_context_agent";
    }

    @Override
    public String displayName() {
        return "Project Context Agent";
    }

    @Override
    public AgentOutcome<ProjectContextOutput> run(Input input, Instant deadline) {
        if (input.projectId() == null) {
            return new AgentOutcome<>(new ProjectContextOutput(null, null, null, null, false,
                    "Defect is not linked to a project, so no project knowledge was searched.", null, input.query(), List.of()),
                    1, List.of());
        }
        Optional<Project> project = projectRepository.findById(input.projectId());
        if (project.isEmpty()) {
            return new AgentOutcome<>(new ProjectContextOutput(input.projectId(), null, null, null, false,
                    "Linked project no longer exists.", null, input.query(), List.of()), 1, List.of());
        }
        ProjectComponent component = input.componentId() == null ? null
                : componentRepository.findByIdAndProjectId(input.componentId(), input.projectId()).orElse(null);
        List<String> warnings = new ArrayList<>();
        if (input.componentId() != null && component == null) {
            warnings.add("Linked component does not belong to the project; component scope was ignored.");
        }

        KnowledgeRetrievalService.RetrievalResult result;
        try {
            result = retrievalService.retrieve(input.user(), input.projectId(),
                    component == null ? null : component.getId(), input.query(), properties.getRetrievalTopK());
        } catch (ApiException exception) {
            throw new AgentException(AgentException.Category.ACCESS_DENIED,
                    "The user who started this investigation can no longer access the project");
        }

        List<EvidenceItem> items = new ArrayList<>();
        int index = 1;
        for (KnowledgeRetrievalService.RetrievedChunk chunk : result.chunks()) {
            String content = chunk.chunk().getContent();
            boolean truncated = content.length() > properties.getMaxEvidenceItemChars();
            items.add(new EvidenceItem(
                    "K" + index++,
                    "PROJECT_KNOWLEDGE",
                    chunk.documentTitle(),
                    truncated ? content.substring(0, properties.getMaxEvidenceItemChars()) : content,
                    new SourceRef("DOCUMENT_CHUNK", null, chunk.chunk().getDocumentId(), chunk.versionNumber(),
                            chunk.filename(), chunk.chunk().getId(), chunk.chunk().getPageNumber(),
                            chunk.chunk().getStartLine(), chunk.chunk().getEndLine(), chunk.score()),
                    chunk.chunk().isSuspicious(),
                    truncated));
        }
        String note = items.isEmpty()
                ? "No relevant indexed project knowledge matched this defect."
                : "Retrieved " + items.size() + " passage(s) from project knowledge.";
        return new AgentOutcome<>(new ProjectContextOutput(project.get().getId(), project.get().getName(),
                component == null ? null : component.getId(), component == null ? null : component.getName(),
                true, note, result.mode(), input.query(), items), 1, warnings);
    }
}
