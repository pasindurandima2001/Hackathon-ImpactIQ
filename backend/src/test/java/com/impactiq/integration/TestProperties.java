package com.impactiq.integration;

/** Shared test overrides: never call Gemini, never write outside the temp directory. */
final class TestProperties {

    static final String NO_GEMINI_KEY = "gemini.api-key=";
    static final String NO_EMBEDDINGS = "impactiq.knowledge.embeddings-enabled=false";
    static final String STORAGE_DIR = "impactiq.knowledge.storage-dir=${java.io.tmpdir}/impactiq-test-storage";
    static final String NO_BOOTSTRAP_ADMIN = "impactiq.auth.bootstrap-admin-username=";
    static final String FAST_RETRIES = "impactiq.investigation.retry-backoff-ms=10";
    /** Keeps background component discovery from making extra calls to a scripted model. */
    static final String NO_AUTO_COMPONENTS = "impactiq.components.auto-discover=false";

    private TestProperties() {
    }
}
