package com.impactiq.integration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;

/**
 * Opt-in harness for browser end-to-end tests: runs the full application on port 8080 (override with IMPACTIQ_E2E_PORT) against an in-memory H2
 * database (never the real impactiq_db). Enable with IMPACTIQ_E2E_SERVER=true; stop by creating target/e2e-stop.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT, properties = {
        "server.port=${IMPACTIQ_E2E_PORT:8080}",
        TestProperties.STORAGE_DIR,
        TestProperties.NO_BOOTSTRAP_ADMIN
})
@AutoConfigureTestDatabase
@EnabledIfEnvironmentVariable(named = "IMPACTIQ_E2E_SERVER", matches = "true")
class E2eServerHarness {

    private static final Duration MAX_RUNTIME = Duration.ofMinutes(20);

    @Test
    void serveUntilStopped() throws InterruptedException {
        Path stopFile = Path.of("target", "e2e-stop");
        Instant deadline = Instant.now().plus(MAX_RUNTIME);
        System.out.println("E2E server ready on http://localhost:8080");
        while (!Files.exists(stopFile) && Instant.now().isBefore(deadline)) {
            Thread.sleep(1000);
        }
    }
}
