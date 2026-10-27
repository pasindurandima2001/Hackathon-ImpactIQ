package com.impactiq.controller;

import com.impactiq.dto.InvestigationDtos.FeedbackRequest;
import com.impactiq.dto.InvestigationDtos.FeedbackResponse;
import com.impactiq.dto.InvestigationDtos.InvestigationResponse;
import com.impactiq.dto.InvestigationDtos.InvestigationSummary;
import com.impactiq.security.CurrentUserService;
import com.impactiq.service.InvestigationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Investigations run asynchronously: start/retry return 202 and clients poll the run until it finishes. */
@RestController
@RequestMapping("/api/defects/{defectId}/investigations")
public class InvestigationController {

    private final InvestigationService investigationService;
    private final CurrentUserService currentUser;

    public InvestigationController(InvestigationService investigationService, CurrentUserService currentUser) {
        this.investigationService = investigationService;
        this.currentUser = currentUser;
    }

    @PostMapping
    public ResponseEntity<InvestigationResponse> start(@PathVariable Long defectId) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(investigationService.start(currentUser.require(), defectId));
    }

    @GetMapping
    public List<InvestigationSummary> list(@PathVariable Long defectId) {
        return investigationService.list(currentUser.require(), defectId);
    }

    @GetMapping("/{investigationId}")
    public InvestigationResponse get(@PathVariable Long defectId, @PathVariable Long investigationId) {
        return investigationService.get(currentUser.require(), defectId, investigationId);
    }

    @PostMapping("/{investigationId}/retry")
    public ResponseEntity<InvestigationResponse> retry(@PathVariable Long defectId, @PathVariable Long investigationId) {
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(investigationService.retry(currentUser.require(), defectId, investigationId));
    }

    @PostMapping("/{investigationId}/feedback")
    public ResponseEntity<FeedbackResponse> feedback(
            @PathVariable Long defectId,
            @PathVariable Long investigationId,
            @Valid @RequestBody FeedbackRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(investigationService.addFeedback(currentUser.require(), defectId, investigationId, request));
    }
}
