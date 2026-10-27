package com.impactiq.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.impactiq.config.AzureDevOpsProperties;
import com.impactiq.exception.ApiException;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AzureDevOpsClientTest {

    private static final String WORK_ITEM = """
            {"id":1995461,"fields":{
              "System.WorkItemType":"Bug","System.Title":"Checkout fails with 504","System.State":"Active",
              "Microsoft.VSTS.Common.Severity":"2 - High","Microsoft.VSTS.Common.Priority":1,
              "System.AreaPath":"PL-Sales-Enablement\\\\Payments",
              "System.AssignedTo":{"displayName":"Someone","uniqueName":"someone@example.com"},
              "Microsoft.VSTS.TCM.ReproSteps":"<div>Open cart</div><ol><li>Pay with card</li><li>Wait</li></ol><p>Got&nbsp;504 &amp; spinner</p>"},
             "relations":[
              {"rel":"AttachedFile","url":"https://example/attachments/1","attributes":{"name":"gateway.log"}},
              {"rel":"System.LinkTypes.Related","url":"https://dev.azure.com/o/_apis/wit/workItems/42","attributes":{"name":"Related"}}]}""";
    private static final String COMMENTS = """
            {"comments":[{"text":"<p>Started after release 4.2</p>","createdDate":"2026-10-01T10:00:00Z"}]}""";

    private HttpServer server;

    @AfterEach
    void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void parsesEditBoardAndLegacyLinks() {
        AzureDevOpsClient client = client("http://127.0.0.1:1", "token", List.of("virtusaeag"));
        AzureDevOpsClient.WorkItemLink link = client.parseLink("https://dev.azure.com/virtusaeag/PL-Sales-Enablement/_workitems/edit/1995461");
        assertEquals("virtusaeag", link.organization());
        assertEquals("PL-Sales-Enablement", link.project());
        assertEquals(1995461L, link.id());

        AzureDevOpsClient.WorkItemLink board = client.parseLink("https://dev.azure.com/virtusaeag/My%20Project/_boards/board/t/Team?workitem=77");
        assertEquals("My Project", board.project());
        assertEquals(77L, board.id());

        AzureDevOpsClient.WorkItemLink legacy = client.parseLink("https://virtusaeag.visualstudio.com/DefaultCollection/Sales/_workitems/edit/5/");
        assertEquals("virtusaeag", legacy.organization());
        assertEquals("Sales", legacy.project());
        assertEquals("https://dev.azure.com/virtusaeag/My%20Project/_workitems/edit/77", board.webUrl());
    }

    @Test
    void rejectsLinksOutsideAzureDevOpsAndOtherOrganizations() {
        AzureDevOpsClient client = client("http://127.0.0.1:1", "token", List.of("virtusaeag"));
        assertEquals(HttpStatus.BAD_REQUEST, status(() -> client.parseLink("https://evil.example.com/virtusaeag/P/_workitems/edit/1")));
        assertEquals(HttpStatus.BAD_REQUEST, status(() -> client.parseLink("http://dev.azure.com/virtusaeag/P/_workitems/edit/1")));
        assertEquals(HttpStatus.BAD_REQUEST, status(() -> client.parseLink("https://dev.azure.com/virtusaeag/P/_workitems")));
        assertEquals(HttpStatus.BAD_REQUEST, status(() -> client.parseLink("https://dev.azure.com/virtusaeag/_apis/wit/workitems/1")));
        assertEquals(HttpStatus.FORBIDDEN, status(() -> client.parseLink("https://dev.azure.com/otherorg/P/_workitems/edit/1")));
    }

    @Test
    void fetchesFieldsCommentsAndRelationsAsPlainText() throws IOException {
        AtomicReference<String> authorization = new AtomicReference<>();
        AtomicReference<String> path = new AtomicReference<>();
        int port = startServer(200, authorization, path);
        AzureDevOpsClient client = client("http://127.0.0.1:" + port, "secret-token", List.of());

        AzureDevOpsClient.WorkItem item = client.fetch("https://dev.azure.com/virtusaeag/PL-Sales-Enablement/_workitems/edit/1995461");

        assertEquals("Basic " + Base64.getEncoder().encodeToString(":secret-token".getBytes(StandardCharsets.UTF_8)),
                authorization.get());
        assertTrue(path.get().startsWith("/virtusaeag/PL-Sales-Enablement/_apis/wit/"));
        assertEquals("Bug", item.workItemType());
        assertEquals("Checkout fails with 504", item.title());
        assertEquals("2 - High", item.severity());
        assertEquals("1", item.priority());
        assertEquals(List.of("gateway.log"), item.attachments());
        assertEquals(List.of("Related #42"), item.links());
        assertEquals(1, item.comments().size());
        String text = item.toText();
        assertTrue(text.contains("Repro steps:\nOpen cart\n\n- Pay with card\n- Wait"), text);
        assertTrue(text.contains("Got 504 & spinner"), text);
        assertTrue(text.contains("[2026-10-01] Started after release 4.2"), text);
        assertTrue(!text.contains("someone@example.com"), "assignee details are not imported");
    }

    @Test
    void reportsRejectedTokenMissingItemAndMissingConfiguration() throws IOException {
        int port = startServer(203, new AtomicReference<>(), new AtomicReference<>());
        String link = "https://dev.azure.com/virtusaeag/P/_workitems/edit/9";
        assertEquals(HttpStatus.BAD_GATEWAY, status(() -> client("http://127.0.0.1:" + port, "bad", List.of()).fetch(link)));
        server.stop(0);

        int missing = startServer(404, new AtomicReference<>(), new AtomicReference<>());
        assertEquals(HttpStatus.NOT_FOUND, status(() -> client("http://127.0.0.1:" + missing, "token", List.of()).fetch(link)));

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, status(() -> client("http://127.0.0.1:" + missing, "", List.of()).fetch(link)));
    }

    @Test
    void convertsHtmlEntitiesAndDropsScripts() {
        assertEquals("a < b\nline 2 é", AzureDevOpsClient.htmlToText("<script>alert(1)</script>a &lt; b<br/>line&nbsp;2 &#233;"));
    }

    private int startServer(int status, AtomicReference<String> authorization, AtomicReference<String> path) throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/", exchange -> {
            String requestPath = exchange.getRequestURI().getPath();
            if (!requestPath.endsWith("/comments")) {
                authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
                path.set(requestPath);
            }
            byte[] bytes = (requestPath.endsWith("/comments") ? COMMENTS : WORK_ITEM).getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            try (OutputStream output = exchange.getResponseBody()) {
                output.write(bytes);
            }
        });
        server.start();
        return server.getAddress().getPort();
    }

    private static HttpStatus status(Runnable call) {
        return assertThrows(ApiException.class, call::run).status();
    }

    private static AzureDevOpsClient client(String baseUrl, String pat, List<String> organizations) {
        AzureDevOpsProperties properties = new AzureDevOpsProperties();
        properties.setBaseUrl(baseUrl);
        properties.setPat(pat);
        properties.setAllowedOrganizations(organizations);
        properties.setTimeoutMs(2_000);
        return new AzureDevOpsClient(properties, new ObjectMapper());
    }
}
