package com.impactiq.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.impactiq.config.AzureDevOpsProperties;
import com.impactiq.exception.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads one Azure DevOps work item (fields, comments, attachment names and links) from a pasted browser link.
 * The link only supplies organization, project and ID; each is validated and requests go to the configured host,
 * so a pasted link can never make the server call another site.
 */
@Component
public class AzureDevOpsClient {

    public static final String WORK_ITEM_API_VERSION = "7.1";
    public static final String COMMENTS_API_VERSION = "7.1-preview.4";
    private static final Logger LOGGER = LoggerFactory.getLogger(AzureDevOpsClient.class);
    private static final String DEV_AZURE_HOST = "dev.azure.com";
    private static final String LEGACY_HOST_SUFFIX = ".visualstudio.com";
    private static final Pattern ORGANIZATION = Pattern.compile("[A-Za-z0-9][A-Za-z0-9-]{0,49}");
    private static final Pattern PROJECT = Pattern.compile("[\\p{L}\\p{N} ._()\\-]{1,64}");
    private static final Pattern WORK_ITEM_ID = Pattern.compile("[1-9][0-9]{0,9}");
    private static final Pattern WORK_ITEM_QUERY = Pattern.compile("(?:^|&)workitem=([0-9]+)");
    private static final int MAX_LINK_LENGTH = 2048;
    private static final int MAX_FIELD_CHARS = 12_000;
    private static final int MAX_COMMENT_CHARS = 2_000;

    private final AzureDevOpsProperties properties;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    public AzureDevOpsClient(AzureDevOpsProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory() {
            @Override
            protected void prepareConnection(HttpURLConnection connection, String httpMethod) throws IOException {
                super.prepareConnection(connection, httpMethod);
                // A rejected token is answered with a redirect to the sign-in page; never follow it.
                connection.setInstanceFollowRedirects(false);
            }
        };
        factory.setConnectTimeout(Duration.ofMillis(properties.getTimeoutMs()));
        factory.setReadTimeout(Duration.ofMillis(properties.getTimeoutMs()));
        this.restClient = RestClient.builder().requestFactory(factory).build();
    }

    /** Organization, project and work item ID taken from a pasted link. */
    public record WorkItemLink(String organization, String project, long id) {

        public String webUrl() {
            return "https://" + DEV_AZURE_HOST + "/" + encode(organization) + "/" + encode(project) + "/_workitems/edit/" + id;
        }
    }

    public record WorkItem(
            WorkItemLink link,
            String workItemType,
            String title,
            String state,
            String severity,
            String priority,
            String areaPath,
            String iterationPath,
            String tags,
            String description,
            String reproSteps,
            String systemInfo,
            String acceptanceCriteria,
            List<String> attachments,
            List<String> links,
            List<String> comments,
            List<String> warnings
    ) {

        /** Plain-text version of the work item, used as drafting input and as the attached evidence. */
        public String toText() {
            StringBuilder text = new StringBuilder();
            text.append("Azure DevOps ").append(workItemType).append(" #").append(link.id()).append(": ").append(title).append('\n');
            line(text, "Project", link.project());
            line(text, "State", state);
            line(text, "Severity", severity);
            line(text, "Priority", priority);
            line(text, "Area path", areaPath);
            line(text, "Iteration", iterationPath);
            line(text, "Tags", tags);
            line(text, "Link", link.webUrl());
            section(text, "Description", description);
            section(text, "Repro steps", reproSteps);
            section(text, "System info", systemInfo);
            section(text, "Acceptance criteria", acceptanceCriteria);
            if (!attachments.isEmpty()) {
                section(text, "Attachments (names only)", String.join("\n", attachments));
            }
            if (!links.isEmpty()) {
                section(text, "Linked items", String.join("\n", links));
            }
            if (!comments.isEmpty()) {
                section(text, "Discussion (" + comments.size() + " comment" + (comments.size() == 1 ? "" : "s") + ")",
                        String.join("\n\n", comments));
            }
            return text.toString().strip();
        }

        private static void line(StringBuilder text, String label, String value) {
            if (value != null && !value.isBlank()) {
                text.append(label).append(": ").append(value).append('\n');
            }
        }

        private static void section(StringBuilder text, String heading, String value) {
            if (value != null && !value.isBlank()) {
                text.append('\n').append(heading).append(":\n").append(value.strip()).append('\n');
            }
        }
    }

    /** Validates a pasted work item link and extracts organization, project and ID. */
    public WorkItemLink parseLink(String rawLink) {
        String link = rawLink == null ? "" : rawLink.strip();
        if (link.isEmpty() || link.length() > MAX_LINK_LENGTH) {
            throw ApiException.badRequest("Paste the link of an Azure DevOps work item");
        }
        URI uri;
        try {
            uri = URI.create(link);
        } catch (IllegalArgumentException exception) {
            throw ApiException.badRequest("That is not a valid link");
        }
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
        if (!"https".equalsIgnoreCase(uri.getScheme())) {
            throw ApiException.badRequest("Use the https:// link of the work item");
        }
        List<String> segments;
        try {
            segments = new ArrayList<>(Arrays.stream(Optional.ofNullable(uri.getRawPath()).orElse("").split("/"))
                    .filter(segment -> !segment.isEmpty())
                    .map(AzureDevOpsClient::decode)
                    .toList());
        } catch (IllegalArgumentException exception) {
            throw ApiException.badRequest("That is not a valid link");
        }
        String organization;
        if (host.equals(DEV_AZURE_HOST)) {
            if (segments.isEmpty()) {
                throw ApiException.badRequest("The link does not contain an organization");
            }
            organization = segments.remove(0);
        } else if (host.endsWith(LEGACY_HOST_SUFFIX) && host.length() > LEGACY_HOST_SUFFIX.length()) {
            organization = host.substring(0, host.length() - LEGACY_HOST_SUFFIX.length());
            if (!segments.isEmpty() && segments.get(0).equalsIgnoreCase("DefaultCollection")) {
                segments.remove(0);
            }
        } else {
            throw ApiException.badRequest("Only links from dev.azure.com or visualstudio.com can be imported");
        }
        if (!ORGANIZATION.matcher(organization).matches()) {
            throw ApiException.badRequest("The organization name in the link is not valid");
        }
        if (!isAllowedOrganization(organization)) {
            throw ApiException.forbidden("Importing from the Azure DevOps organization '" + organization + "' is not allowed");
        }
        if (segments.isEmpty() || segments.get(0).startsWith("_") || !PROJECT.matcher(segments.get(0)).matches()) {
            throw ApiException.badRequest("The link does not contain a valid project name");
        }
        String project = segments.get(0);
        String id = workItemId(segments, uri.getRawQuery());
        if (id == null || !WORK_ITEM_ID.matcher(id).matches()) {
            throw ApiException.badRequest("The link does not point to a work item. Open the work item and copy its link.");
        }
        return new WorkItemLink(organization, project, Long.parseLong(id));
    }

    /** Fetches the work item behind a pasted link, with its comments, attachment names and links. */
    public WorkItem fetch(String rawLink) {
        WorkItemLink link = parseLink(rawLink);
        if (properties.getPat() == null || properties.getPat().isBlank()) {
            throw ApiException.unavailable("Azure DevOps import is not set up. Set the AZURE_DEVOPS_PAT environment variable "
                    + "(a token with Work Items: Read) and restart the backend.");
        }
        String projectBase = properties.getBaseUrl().replaceAll("/+$", "") + "/" + encode(link.organization()) + "/"
                + encode(link.project()) + "/_apis/wit";
        JsonNode item = get(projectBase + "/workitems/" + link.id() + "?$expand=all&api-version=" + WORK_ITEM_API_VERSION, link);
        JsonNode fields = item.path("fields");
        List<String> warnings = new ArrayList<>();
        List<String> comments = new ArrayList<>();
        try {
            JsonNode commentPage = get(projectBase + "/workItems/" + link.id() + "/comments?$top=" + properties.getMaxComments()
                    + "&api-version=" + COMMENTS_API_VERSION, link);
            for (JsonNode comment : commentPage.path("comments")) {
                String body = truncate(htmlToText(comment.path("text").asText("")), MAX_COMMENT_CHARS);
                if (!body.isBlank()) {
                    String date = comment.path("createdDate").asText("");
                    comments.add((date.length() >= 10 ? "[" + date.substring(0, 10) + "] " : "") + body);
                }
            }
        } catch (ApiException exception) {
            warnings.add("Comments could not be read from Azure DevOps, so the draft uses the work item fields only.");
        }
        List<String> attachments = new ArrayList<>();
        List<String> links = new ArrayList<>();
        for (JsonNode relation : item.path("relations")) {
            String rel = relation.path("rel").asText("");
            String name = relation.path("attributes").path("name").asText("");
            if (rel.equals("AttachedFile")) {
                attachments.add(name.isBlank() ? "Unnamed attachment" : name);
            } else if (!name.isBlank()) {
                String url = relation.path("url").asText("");
                String target = url.matches(".*/workItems/[0-9]+$") ? " #" + url.substring(url.lastIndexOf('/') + 1) : "";
                links.add(name + target);
            }
        }
        return new WorkItem(
                link,
                text(fields, "System.WorkItemType", "Work item"),
                text(fields, "System.Title", "Work item " + link.id()),
                text(fields, "System.State", ""),
                text(fields, "Microsoft.VSTS.Common.Severity", ""),
                text(fields, "Microsoft.VSTS.Common.Priority", ""),
                text(fields, "System.AreaPath", ""),
                text(fields, "System.IterationPath", ""),
                text(fields, "System.Tags", ""),
                html(fields, "System.Description"),
                html(fields, "Microsoft.VSTS.TCM.ReproSteps"),
                html(fields, "Microsoft.VSTS.TCM.SystemInfo"),
                html(fields, "Microsoft.VSTS.Common.AcceptanceCriteria"),
                attachments, links, comments, warnings);
    }

    private JsonNode get(String url, WorkItemLink link) {
        try {
            return restClient.get()
                    .uri(URI.create(url))
                    .header("Authorization", "Basic " + Base64.getEncoder()
                            .encodeToString((":" + properties.getPat()).getBytes(StandardCharsets.UTF_8)))
                    .header("Accept", "application/json")
                    .exchange((request, response) -> {
                        int status = response.getStatusCode().value();
                        if (status == 200) {
                            try (InputStream body = response.getBody()) {
                                return objectMapper.readTree(body);
                            }
                        }
                        throw failure(status, link);
                    });
        } catch (RestClientException exception) {
            LOGGER.warn("Azure DevOps request failed: {}", exception.getClass().getSimpleName());
            throw new ApiException(HttpStatus.BAD_GATEWAY, "Azure DevOps could not be reached. Check the network and try again.");
        }
    }

    private static ApiException failure(int status, WorkItemLink link) {
        if (status == 203 || status == 401 || (status >= 300 && status < 400)) {
            // 203 and redirects are how Azure DevOps answers an expired or invalid token.
            return new ApiException(HttpStatus.BAD_GATEWAY,
                    "Azure DevOps rejected the access token. Check that AZURE_DEVOPS_PAT is valid and has Work Items: Read.");
        }
        if (status == 403) {
            return ApiException.forbidden("The Azure DevOps token cannot read project '" + link.project() + "'.");
        }
        if (status == 404) {
            return ApiException.notFound("Work item " + link.id() + " was not found in '" + link.project()
                    + "', or the token cannot see it.");
        }
        if (status == 429) {
            return ApiException.tooManyRequests("Azure DevOps is limiting requests. Wait a minute and try again.");
        }
        return new ApiException(HttpStatus.BAD_GATEWAY, "Azure DevOps returned an error (HTTP " + status + "). Try again later.");
    }

    private boolean isAllowedOrganization(String organization) {
        List<String> allowed = properties.getAllowedOrganizations();
        return allowed == null || allowed.stream().noneMatch(value -> value != null && !value.isBlank())
                || allowed.stream().anyMatch(value -> value != null && value.strip().equalsIgnoreCase(organization));
    }

    private static String workItemId(List<String> segments, String rawQuery) {
        int index = segments.indexOf("_workitems");
        if (index >= 0 && index + 2 < segments.size() && segments.get(index + 1).equals("edit")) {
            return segments.get(index + 2);
        }
        if (rawQuery != null) {
            Matcher matcher = WORK_ITEM_QUERY.matcher(rawQuery);
            if (matcher.find()) {
                return matcher.group(1);
            }
        }
        return null;
    }

    private static String text(JsonNode fields, String name, String fallback) {
        JsonNode value = fields.path(name);
        if (value.isMissingNode() || value.isNull()) {
            return fallback;
        }
        String text = value.isObject() ? value.path("displayName").asText(fallback) : value.asText(fallback);
        return text.isBlank() ? fallback : text.strip();
    }

    private static String html(JsonNode fields, String name) {
        return truncate(htmlToText(text(fields, name, "")), MAX_FIELD_CHARS);
    }

    /** Converts Azure DevOps rich-text HTML to readable plain text. */
    public static String htmlToText(String html) {
        if (html == null || html.isBlank()) {
            return "";
        }
        String text = html
                .replaceAll("(?is)<(script|style)[^>]*>.*?</\\1>", "")
                .replaceAll("(?i)<br\\s*/?>", "\n")
                .replaceAll("(?i)<li[^>]*>", "\n- ")
                .replaceAll("(?i)</(p|div|h[1-6]|tr|ul|ol|pre|blockquote)>", "\n")
                .replaceAll("(?i)</t[dh]>", "\t")
                .replaceAll("(?s)<[^>]*>", "");
        text = decodeEntities(text)
                .replace(' ', ' ')
                .replaceAll("[ \\t]+\\n", "\n")
                .replaceAll("\\n{3,}", "\n\n");
        return text.strip();
    }

    private static String decodeEntities(String text) {
        Matcher numeric = Pattern.compile("&#(x?)([0-9a-fA-F]{1,6});").matcher(text);
        StringBuilder decoded = new StringBuilder();
        while (numeric.find()) {
            int codePoint;
            try {
                codePoint = Integer.parseInt(numeric.group(2), numeric.group(1).isEmpty() ? 10 : 16);
            } catch (NumberFormatException exception) {
                codePoint = '?';
            }
            String replacement = Character.isValidCodePoint(codePoint) ? new String(Character.toChars(codePoint)) : "?";
            numeric.appendReplacement(decoded, Matcher.quoteReplacement(replacement));
        }
        numeric.appendTail(decoded);
        return decoded.toString()
                .replace("&nbsp;", " ")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&apos;", "'")
                .replace("&amp;", "&");
    }

    private static String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max) + "…";
    }

    private static String decode(String segment) {
        return URLDecoder.decode(segment.replace("+", "%2B"), StandardCharsets.UTF_8);
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
