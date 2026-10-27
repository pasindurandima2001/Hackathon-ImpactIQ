package com.impactiq.controller;

import com.impactiq.dto.InvestigationDtos.EvidenceRequest;
import com.impactiq.dto.InvestigationDtos.EvidenceResponse;
import com.impactiq.entity.EvidenceType;
import com.impactiq.security.CurrentUserService;
import com.impactiq.service.EvidenceService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/defects/{defectId}/evidence")
public class EvidenceController {

    private final EvidenceService evidenceService;
    private final CurrentUserService currentUser;

    public EvidenceController(EvidenceService evidenceService, CurrentUserService currentUser) {
        this.evidenceService = evidenceService;
        this.currentUser = currentUser;
    }

    @GetMapping
    public List<EvidenceResponse> list(@PathVariable Long defectId) {
        return evidenceService.list(currentUser.require(), defectId);
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<EvidenceResponse> add(@PathVariable Long defectId, @Valid @RequestBody EvidenceRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(evidenceService.add(currentUser.require(), defectId, request));
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<EvidenceResponse> upload(
            @PathVariable Long defectId,
            @RequestPart("file") MultipartFile file,
            @RequestParam(required = false) EvidenceType type,
            @RequestParam(required = false) String label
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(evidenceService.upload(currentUser.require(), defectId, type, label, file));
    }

    @DeleteMapping("/{evidenceId}")
    public ResponseEntity<Void> delete(@PathVariable Long defectId, @PathVariable Long evidenceId) {
        evidenceService.delete(currentUser.require(), defectId, evidenceId);
        return ResponseEntity.noContent().build();
    }
}
