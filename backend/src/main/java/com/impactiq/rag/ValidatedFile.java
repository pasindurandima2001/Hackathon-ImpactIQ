package com.impactiq.rag;

/** An upload that passed size, name, extension and content checks. */
public record ValidatedFile(
        byte[] bytes,
        String filename,
        String extension,
        String contentType,
        String sha256
) {

    public boolean isPdf() {
        return "pdf".equals(extension);
    }
}
