package com.impactiq.exception;

public class ImpactAnalysisNotFoundException extends RuntimeException {

    public ImpactAnalysisNotFoundException(Long defectId) {
        super("No impact analysis found for defect " + defectId);
    }
}
