package com.impactiq.exception;

public class DefectNotFoundException extends RuntimeException {

    public DefectNotFoundException(Long id) {
        super("Defect with id " + id + " was not found");
    }
}
