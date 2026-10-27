package com.impactiq.service;

import com.impactiq.dto.InvestigationDtos;
import com.impactiq.dto.InvestigationDtos.EvidenceRequest;
import com.impactiq.dto.InvestigationDtos.EvidenceResponse;
import com.impactiq.entity.Defect;
import com.impactiq.entity.DefectEvidence;
import com.impactiq.entity.EvidenceType;
import com.impactiq.entity.ProjectRole;
import com.impactiq.exception.ApiException;
import com.impactiq.exception.DefectNotFoundException;
import com.impactiq.rag.FileValidationService;
import com.impactiq.rag.PromptInjectionDetector;
import com.impactiq.rag.SecretRedactor;
import com.impactiq.rag.TextExtractionService;
import com.impactiq.rag.ValidatedFile;
import com.impactiq.repository.DefectEvidenceRepository;
import com.impactiq.repository.DefectRepository;
import com.impactiq.security.CurrentUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Locale;

/**
 * Evidence attached to a defect. Content is secret-redacted and flagged for instruction-like text before it is
 * stored; uploaded files are validated and only their extracted text is kept.
 */
@Service
@Transactional
public class EvidenceService {

    private static final int MAX_EVIDENCE_PER_DEFECT = 50;
    private static final java.util.regex.Pattern STACK_FRAME = java.util.regex.Pattern.compile("\\bat [\\w.$]+\\(");

    private final DefectEvidenceRepository evidenceRepository;
    private final DefectRepository defectRepository;
    private final AccessControlService accessControl;
    private final SecretRedactor secretRedactor;
    private final PromptInjectionDetector injectionDetector;
    private final FileValidationService fileValidationService;
    private final TextExtractionService extractionService;
    private final AuditService auditService;

    public EvidenceService(
            DefectEvidenceRepository evidenceRepository,
            DefectRepository defectRepository,
            AccessControlService accessControl,
            SecretRedactor secretRedactor,
            PromptInjectionDetector injectionDetector,
            FileValidationService fileValidationService,
            TextExtractionService extractionService,
            AuditService auditService
    ) {
        this.evidenceRepository = evidenceRepository;
        this.defectRepository = defectRepository;
        this.accessControl = accessControl;
        this.secretRedactor = secretRedactor;
        this.injectionDetector = injectionDetector;
        this.fileValidationService = fileValidationService;
        this.extractionService = extractionService;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<EvidenceResponse> list(CurrentUser user, Long defectId) {
        requireDefect(user, defectId, ProjectRole.VIEWER);
        return evidenceRepository.findByDefectIdOrderByCreatedAtAscIdAsc(defectId).stream().map(this::toResponse).toList();
    }

    public EvidenceResponse add(CurrentUser user, Long defectId, EvidenceRequest request) {
        Defect defect = requireDefect(user, defectId, ProjectRole.EDITOR);
        return save(user, defect, request.type(), request.label().trim(), request.content(), null);
    }

    public EvidenceResponse upload(CurrentUser user, Long defectId, EvidenceType type, String label, MultipartFile file) {
        Defect defect = requireDefect(user, defectId, ProjectRole.EDITOR);
        ValidatedFile validated = fileValidationService.validate(file);
        String text;
        try {
            text = extractionService.extract(validated).fullText();
        } catch (TextExtractionService.ExtractionException exception) {
            throw ApiException.badRequest(exception.getMessage());
        }
        if (text.length() > InvestigationDtos.EVIDENCE_MAX_CHARS) {
            text = text.substring(0, InvestigationDtos.EVIDENCE_MAX_CHARS);
        }
        EvidenceType resolvedType = type == null ? guessType(validated.extension(), text) : type;
        String resolvedLabel = label == null || label.isBlank() ? validated.filename() : label.trim();
        return save(user, defect, resolvedType, truncate(resolvedLabel, 255), text, validated.filename());
    }

    public void delete(CurrentUser user, Long defectId, Long evidenceId) {
        Defect defect = requireDefect(user, defectId, ProjectRole.EDITOR);
        DefectEvidence evidence = evidenceRepository.findByIdAndDefectId(evidenceId, defectId)
                .orElseThrow(() -> ApiException.notFound("Evidence " + evidenceId + " was not found"));
        evidenceRepository.delete(evidence);
        auditService.record(user, "EVIDENCE_DELETED", "EVIDENCE", evidenceId, defect.getProjectId(), "defect=" + defectId);
    }

    private EvidenceResponse save(CurrentUser user, Defect defect, EvidenceType type, String label, String content, String filename) {
        if (evidenceRepository.countByDefectId(defect.getId()) >= MAX_EVIDENCE_PER_DEFECT) {
            throw ApiException.conflict("A defect can have at most " + MAX_EVIDENCE_PER_DEFECT + " evidence items");
        }
        String normalized = content.replace("\r\n", "\n").replace('\r', '\n');
        SecretRedactor.RedactionResult redacted = secretRedactor.redact(normalized);
        DefectEvidence evidence = new DefectEvidence();
        evidence.setDefectId(defect.getId());
        evidence.setType(type);
        evidence.setLabel(label);
        evidence.setContent(redacted.text());
        evidence.setSourceFilename(filename);
        evidence.setContentSha256(FileValidationService.sha256(redacted.text()));
        evidence.setRedactionCount(redacted.redactionCount());
        evidence.setSuspicious(injectionDetector.isSuspicious(redacted.text()));
        evidence.setCreatedBy(user.id());
        DefectEvidence saved = evidenceRepository.saveAndFlush(evidence);
        auditService.record(user, "EVIDENCE_ADDED", "EVIDENCE", saved.getId(), defect.getProjectId(),
                "defect=" + defect.getId() + " type=" + type + " redactions=" + redacted.redactionCount());
        return toResponse(saved);
    }

    static EvidenceType guessType(String extension, String text) {
        String lower = extension == null ? "" : extension.toLowerCase(Locale.ROOT);
        if (text.contains("\tat ") || text.contains("Traceback (most recent call last)") || STACK_FRAME.matcher(text).find()) {
            return EvidenceType.STACK_TRACE;
        }
        return switch (lower) {
            case "log" -> EvidenceType.LOG;
            case "json", "yaml", "yml", "properties", "xml" -> EvidenceType.CONFIGURATION;
            case "java", "kt", "js", "jsx", "ts", "tsx", "py", "go", "cs", "sql", "sh", "html", "css" -> EvidenceType.CODE;
            default -> EvidenceType.NOTE;
        };
    }

    private Defect requireDefect(CurrentUser user, Long defectId, ProjectRole role) {
        Defect defect = defectRepository.findById(defectId).orElseThrow(() -> new DefectNotFoundException(defectId));
        accessControl.requireDefectAccess(user, defect, role);
        return defect;
    }

    private EvidenceResponse toResponse(DefectEvidence evidence) {
        return new EvidenceResponse(evidence.getId(), evidence.getDefectId(), evidence.getType(), evidence.getLabel(),
                evidence.getContent(), evidence.getSourceFilename(), evidence.getContentSha256(), evidence.getRedactionCount(),
                evidence.isSuspicious(), evidence.getContent().split("\n", -1).length, evidence.getCreatedAt());
    }

    private static String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }
}
