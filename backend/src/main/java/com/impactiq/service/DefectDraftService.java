package com.impactiq.service;

import com.impactiq.agent.AgentException;
import com.impactiq.agent.DefectDraftAgent;
import com.impactiq.dto.DraftDtos;
import com.impactiq.dto.DraftDtos.DraftMode;
import com.impactiq.dto.DraftDtos.DraftResponse;
import com.impactiq.dto.DraftDtos.EvidenceDraft;
import com.impactiq.dto.DraftDtos.ExternalWorkItem;
import com.impactiq.entity.BusinessCriticality;
import com.impactiq.entity.CustomerImpact;
import com.impactiq.entity.Environment;
import com.impactiq.entity.EvidenceType;
import com.impactiq.entity.ProjectRole;
import com.impactiq.entity.TechnicalImpact;
import com.impactiq.exception.ApiException;
import com.impactiq.integration.AzureDevOpsClient;
import com.impactiq.rag.FileValidationService;
import com.impactiq.rag.SecretRedactor;
import com.impactiq.rag.TextExtractionService;
import com.impactiq.rag.ValidatedFile;
import com.impactiq.repository.ProjectComponentRepository;
import com.impactiq.repository.ProjectRepository;
import com.impactiq.security.CurrentUser;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * AI-assisted defect drafting for Quick Add, Paste Error and Upload modes. Drafts are never persisted here:
 * the developer edits and confirms them through the normal create endpoint. Input is secret-redacted before
 * it reaches the model. When the model is unavailable a clearly labelled heuristic draft is returned.
 */
@Service
public class DefectDraftService {

    private static final long DRAFT_TIMEOUT_MS = 60_000;
    private static final int TITLE_MAX = 120;
    private static final int DESCRIPTION_MAX = 6000;
    private static final String UNKNOWN = "UNKNOWN";

    private final DefectDraftAgent draftAgent;
    private final SecretRedactor secretRedactor;
    private final FileValidationService fileValidationService;
    private final TextExtractionService extractionService;
    private final AccessControlService accessControl;
    private final ProjectRepository projectRepository;
    private final ProjectComponentRepository componentRepository;
    private final AzureDevOpsClient azureDevOpsClient;

    public DefectDraftService(
            DefectDraftAgent draftAgent,
            SecretRedactor secretRedactor,
            FileValidationService fileValidationService,
            TextExtractionService extractionService,
            AccessControlService accessControl,
            ProjectRepository projectRepository,
            ProjectComponentRepository componentRepository,
            AzureDevOpsClient azureDevOpsClient
    ) {
        this.azureDevOpsClient = azureDevOpsClient;
        this.draftAgent = draftAgent;
        this.secretRedactor = secretRedactor;
        this.fileValidationService = fileValidationService;
        this.extractionService = extractionService;
        this.accessControl = accessControl;
        this.projectRepository = projectRepository;
        this.componentRepository = componentRepository;
    }

    public DraftResponse draft(CurrentUser user, DraftDtos.DraftRequest request) {
        if (request.mode() == DraftMode.AZURE_DEVOPS) {
            throw ApiException.badRequest("Use the Azure DevOps import to draft from a work item link");
        }
        return draft(user, request.mode(), request.text(), null, null, request.projectId(), request.componentId());
    }

    public DraftResponse draftFromUpload(CurrentUser user, MultipartFile file, String note, Long projectId, Long componentId) {
        ValidatedFile validated = fileValidationService.validate(file);
        String text;
        try {
            text = extractionService.extract(validated).fullText();
        } catch (TextExtractionService.ExtractionException exception) {
            throw ApiException.badRequest(exception.getMessage());
        }
        if (note != null && !note.isBlank()) {
            text = "Reporter note: " + note.strip() + "\n\n" + text;
        }
        return draft(user, DraftMode.UPLOAD, text, validated.filename(), null, projectId, componentId);
    }

    /**
     * Imports an Azure DevOps work item from its browser link and drafts a defect from it. The original title and a
     * link back to the work item are kept; the full work item text is offered as evidence.
     */
    public DraftResponse draftFromAzureDevOps(CurrentUser user, DraftDtos.AzureDevOpsDraftRequest request) {
        // Check project access before calling Azure DevOps, so a denied request makes no external call.
        projectContext(user, request.projectId(), request.componentId());
        AzureDevOpsClient.WorkItem item = azureDevOpsClient.fetch(request.url());
        String label = "Azure DevOps " + item.workItemType() + " #" + item.link().id();
        DraftResponse draft = draft(user, DraftMode.AZURE_DEVOPS, item.toText(), null, label,
                request.projectId(), request.componentId());
        String title = item.title().length() > TITLE_MAX ? item.title().substring(0, TITLE_MAX - 1) + "…" : item.title();
        String description = (draft.description() == null ? "" : draft.description().strip())
                + "\n\nSource: " + label + " (" + item.link().webUrl() + ")";
        List<String> warnings = new ArrayList<>(item.warnings());
        warnings.addAll(draft.warnings());
        ExternalWorkItem external = new ExternalWorkItem("AZURE_DEVOPS", item.link().id(), item.workItemType(), item.title(),
                item.state(), item.severity(), item.priority(), item.areaPath(), item.link().webUrl(), item.attachments(),
                item.comments().size());
        return new DraftResponse(draft.source(), draft.engine(), title, description, draft.environment(), draft.service(),
                draft.businessFunction(), draft.affectedUsers(), draft.businessCriticality(), draft.customerImpact(),
                draft.technicalImpact(), draft.category(), draft.missingInformation(), draft.rationale(), draft.evidence(),
                warnings, external);
    }

    private DraftResponse draft(CurrentUser user, DraftMode mode, String rawText, String filename, String evidenceLabel,
                                Long projectId, Long componentId) {
        String projectContext = projectContext(user, projectId, componentId);
        String bounded = rawText.length() > DraftDtos.DRAFT_INPUT_MAX_CHARS ? rawText.substring(0, DraftDtos.DRAFT_INPUT_MAX_CHARS) : rawText;
        SecretRedactor.RedactionResult redacted = secretRedactor.redact(bounded.replace("\r\n", "\n"));
        List<String> warnings = new ArrayList<>();
        if (redacted.redactionCount() > 0) {
            warnings.add(redacted.redactionCount() + " secret-like value(s) were redacted before analysis.");
        }
        EvidenceDraft evidence = mode == DraftMode.QUICK ? null : new EvidenceDraft(
                EvidenceServiceTypes.guess(filename, redacted.text()),
                evidenceLabel != null ? evidenceLabel : filename == null ? "Pasted error output" : filename,
                redacted.text(), filename, redacted.redactionCount());
        try {
            DefectDraftAgent.DraftOutput output = draftAgent.draft(mode.name(), redacted.text(), projectContext,
                    Instant.now().plusMillis(DRAFT_TIMEOUT_MS));
            warnings.add("AI suggestions: review and edit every field before saving.");
            return new DraftResponse("AI", draftAgent.engine(), output.title(), output.description(),
                    toEnum(output.environment(), Environment::valueOf), output.service(), output.businessFunction(),
                    output.affectedUsers(),
                    toEnum(output.businessCriticality(), BusinessCriticality::valueOf),
                    toEnum(output.customerImpact(), CustomerImpact::valueOf),
                    toEnum(output.technicalImpact(), TechnicalImpact::valueOf),
                    output.category(), output.missingInformation(), output.rationale(), evidence, warnings);
        } catch (AgentException exception) {
            warnings.add("AI drafting is unavailable (" + exception.getMessage() + "). A basic draft was extracted instead.");
            return heuristic(redacted.text(), evidence, warnings);
        }
    }

    private String projectContext(CurrentUser user, Long projectId, Long componentId) {
        if (projectId == null) {
            if (componentId != null) {
                throw ApiException.badRequest("Select a project before selecting a component");
            }
            return null;
        }
        accessControl.requireProjectRole(user, projectId, ProjectRole.VIEWER);
        String project = projectRepository.findById(projectId).map(item -> item.getName()).orElse("");
        if (componentId == null) {
            return "Project: " + project;
        }
        String component = componentRepository.findByIdAndProjectId(componentId, projectId)
                .map(item -> item.getName())
                .orElseThrow(() -> ApiException.badRequest("Component does not belong to the selected project"));
        return "Project: " + project + "; component: " + component;
    }

    static DraftResponse heuristic(String text, EvidenceDraft evidence, List<String> warnings) {
        String[] lines = text.strip().split("\n");
        String title = lines.length == 0 ? "New defect" : lines[0].strip();
        for (String line : lines) {
            String candidate = line.strip();
            if (candidate.contains("Exception") || candidate.contains("Error") || candidate.contains("FAILED")
                    || candidate.contains("Traceback")) {
                title = candidate;
                break;
            }
        }
        if (title.length() > TITLE_MAX) {
            title = title.substring(0, TITLE_MAX - 1) + "…";
        }
        String description = text.length() > DESCRIPTION_MAX ? text.substring(0, DESCRIPTION_MAX) : text;
        return new DraftResponse("HEURISTIC", null, title.isBlank() ? "New defect" : title, description, null, "", "",
                null, null, null, null, "OTHER",
                List.of("Environment", "Affected service", "Number of affected users", "Steps to reproduce"),
                "Generated without AI: the title is the first error-like line of the input.", evidence, warnings);
    }

    private static <E> E toEnum(String value, Function<String, E> parser) {
        if (value == null || value.isBlank() || UNKNOWN.equals(value)) {
            return null;
        }
        try {
            return parser.apply(value);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    /** Shares the evidence type heuristic with EvidenceService. */
    static final class EvidenceServiceTypes {
        private EvidenceServiceTypes() {
        }

        static EvidenceType guess(String filename, String text) {
            String extension = filename == null || filename.lastIndexOf('.') < 0 ? "" : filename.substring(filename.lastIndexOf('.') + 1);
            return EvidenceService.guessType(extension, text);
        }
    }
}
