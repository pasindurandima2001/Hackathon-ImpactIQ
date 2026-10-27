package com.impactiq.agent;

import com.impactiq.agent.AgentContracts.MlSignal;
import com.impactiq.config.MlProperties;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MlTriageClientTest {

    private static final String RESPONSE = """
            {"severity":[{"label":"CRITICAL","probability":0.61},{"label":"HIGH","probability":0.2}],
             "component":[{"label":"Networking","probability":0.4}],"source":"test model"}""";

    private HttpServer server;

    @AfterEach
    void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void disabledClientNeverCallsTheService() {
        assertTrue(new MlTriageClient(properties(false, "http://127.0.0.1:1")).predict("Crash", "d").isEmpty());
    }

    @Test
    void parsesPrediction() throws IOException {
        int port = startServer(200, RESPONSE);
        Optional<MlSignal> signal = new MlTriageClient(properties(true, "http://127.0.0.1:" + port + "/")).predict("Crash", "d");
        assertTrue(signal.isPresent());
        assertEquals("CRITICAL", signal.get().severity().get(0).label());
        assertEquals(0.61, signal.get().severity().get(0).probability());
        assertEquals("Networking", signal.get().component().get(0).label());
    }

    @Test
    void serviceErrorsAreSwallowed() throws IOException {
        int port = startServer(503, "{\"detail\":\"no models\"}");
        assertTrue(new MlTriageClient(properties(true, "http://127.0.0.1:" + port)).predict("Crash", "d").isEmpty());
        server.stop(0);
        server = null;
        assertTrue(new MlTriageClient(properties(true, "http://127.0.0.1:" + port)).predict("Crash", "d").isEmpty());
    }

    private int startServer(int status, String body) throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/predict", exchange -> {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            try (OutputStream output = exchange.getResponseBody()) {
                output.write(bytes);
            }
        });
        server.start();
        return server.getAddress().getPort();
    }

    private static MlProperties properties(boolean enabled, String url) {
        MlProperties properties = new MlProperties();
        properties.setEnabled(enabled);
        properties.setUrl(url);
        properties.setTimeoutMs(1_000);
        return properties;
    }
}
