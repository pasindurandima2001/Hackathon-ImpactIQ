package com.impactiq.controller;

import com.impactiq.dto.GeminiImpactAnalysisResponse;
import com.impactiq.dto.ImpactAnalysisResponse;
import com.impactiq.entity.ProjectRole;
import com.impactiq.service.DefectService;
import com.impactiq.service.ImpactAnalysisService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.List;

@RestController
@RequestMapping("/api/defects/{defectId}")
public class ImpactAnalysisController {

    private final ImpactAnalysisService impactAnalysisService;
    private final DefectService defectService;

    public ImpactAnalysisController(ImpactAnalysisService impactAnalysisService, DefectService defectService) {
        this.impactAnalysisService = impactAnalysisService;
        this.defectService = defectService;
    }

    @PostMapping("/impact-analysis")
    public GeminiImpactAnalysisResponse legacyAnalyze(@PathVariable Long defectId) {
        defectService.requireAccess(defectId, ProjectRole.EDITOR);
        return impactAnalysisService.analyzeDefect(defectId);
    }

    @PostMapping("/analyze")
    public ResponseEntity<ImpactAnalysisResponse> analyze(@PathVariable Long defectId) {
        defectService.requireAccess(defectId, ProjectRole.EDITOR);
        return createdAnalysis(defectId, impactAnalysisService.analyze(defectId));
    }

    @PostMapping("/reanalyze")
    public ResponseEntity<ImpactAnalysisResponse> reanalyze(@PathVariable Long defectId) {
        defectService.requireAccess(defectId, ProjectRole.EDITOR);
        return createdAnalysis(defectId, impactAnalysisService.reanalyze(defectId));
    }

    @GetMapping("/analysis")
    public ImpactAnalysisResponse getLatest(@PathVariable Long defectId) {
        defectService.requireAccess(defectId, ProjectRole.VIEWER);
        return impactAnalysisService.getLatest(defectId);
    }

    @GetMapping("/analysis/history")
    public List<ImpactAnalysisResponse> getHistory(@PathVariable Long defectId) {
        defectService.requireAccess(defectId, ProjectRole.VIEWER);
        return impactAnalysisService.getHistory(defectId);
    }

    private ResponseEntity<ImpactAnalysisResponse> createdAnalysis(Long defectId, ImpactAnalysisResponse response) {
        var location = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/api/defects/{defectId}/analysis")
                .buildAndExpand(defectId)
                .toUri();
        return ResponseEntity.created(location).body(response);
    }
}
