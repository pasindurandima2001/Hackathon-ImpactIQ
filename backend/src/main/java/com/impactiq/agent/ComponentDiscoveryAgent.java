package com.impactiq.agent;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Reads excerpts of a project's indexed documents and proposes the software components (services, modules,
 * applications) they describe. It has no tools; the server validates every proposal: a component is kept only
 * when its name actually occurs in a source it cites, so the model cannot invent components.
 */
@Component
public class ComponentDiscoveryAgent {

    public static final String NAME = "component_discovery_agent";
    static final int NAME_MAX = 60;
    static final int DESCRIPTION_MAX = 300;
    private static final int MAX_PROPOSALS = 15;
    private static final Pattern ALLOWED_NAME = Pattern.compile("[\\p{L}\\p{N}][\\p{L}\\p{N} ._/&()+#-]{1,59}");
    /** Names too generic to scope retrieval. Compared after normalisation. */
    private static final Set<String> GENERIC = Set.of("system", "application", "app", "project", "service", "services",
            "module", "modules", "component", "components", "backend", "frontend", "database", "api", "server", "client",
            "ui", "documentation", "docs", "readme", "overview", "introduction", "other", "misc", "general", "unknown");

    private static final String SYSTEM = PromptSupport.system(
            "You are the ImpactIQ Component Discovery agent for a software project knowledge base.",
            """
            Read the document excerpts in the untrusted_data block and list the distinct software components they
            describe: services, microservices, modules, applications, libraries, subsystems, integrations or major
            screens that a developer would use to group knowledge and defects.
            Use the exact name as written in the documents, in its most common form, at most 60 characters.
            Do not list the project itself, people, teams, companies, environments, generic words such as backend,
            frontend, database or API on their own, file names, classes of minor importance, or technologies that are
            only mentioned in passing. Skip components listed under existingComponents.
            Give each component a one-sentence description based only on the excerpts, and cite the source IDs
            (for example S1) where its name appears. Prefer fewer, clearly supported components over many weak ones.
            Return an empty list when the excerpts describe no identifiable components.
            """);

    private static final Map<String, Object> SCHEMA = AgentSchemas.object(Map.of(
            "components", AgentSchemas.array(AgentSchemas.object(Map.of(
                    "name", AgentSchemas.string(NAME_MAX),
                    "description", AgentSchemas.string(DESCRIPTION_MAX),
                    "sourceIds", AgentSchemas.stringArray(6, 8)
            ), List.of("name", "description", "sourceIds")), MAX_PROPOSALS)
    ), List.of("components"));

    private final StructuredAgentInvoker invoker;

    public ComponentDiscoveryAgent(StructuredAgentInvoker invoker) {
        this.invoker = invoker;
    }

    /** One document excerpt. The ID (S1, S2, ...) is assigned by the caller. */
    public record Source(String id, String title, String text) {
    }

    public record Request(String projectName, Collection<String> existingComponents, List<Source> sources) {
    }

    public record Proposal(String name, String description, List<String> sourceIds) {
    }

    public record DiscoveryOutput(List<Proposal> components) {
    }

    public List<Proposal> discover(Request request, Instant deadline) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("projectName", request.projectName());
        payload.put("existingComponents", request.existingComponents());
        payload.put("sources", request.sources());
        String prompt = "List the software components described in these documents.\n" + PromptSupport.untrusted(payload);
        StructuredAgentInvoker.Result<DiscoveryOutput> result = invoker.invoke(NAME,
                "Finds software components described in project documents", SYSTEM, prompt, SCHEMA,
                DiscoveryOutput.class, output -> {
                    if (output.components() == null) {
                        throw new AgentException(AgentException.Category.INVALID_OUTPUT, "Agent output is missing components");
                    }
                    return output;
                }, deadline);
        return validate(result.output().components(), request);
    }

    /**
     * Keeps proposals whose name is well formed, not generic, not already present (or the project name), cites a
     * supplied source, and literally occurs in one of the cited sources (ignoring case, spacing and punctuation).
     */
    static List<Proposal> validate(List<Proposal> proposals, Request request) {
        Map<String, String> sourceText = new LinkedHashMap<>();
        request.sources().forEach(source -> sourceText.put(source.id().toUpperCase(Locale.ROOT), normalize(source.text())));
        Set<String> seen = new HashSet<>();
        request.existingComponents().forEach(name -> seen.add(normalize(name)));
        seen.add(normalize(request.projectName()));
        List<Proposal> kept = new ArrayList<>();
        for (Proposal proposal : proposals == null ? List.<Proposal>of() : proposals) {
            if (proposal == null) {
                continue;
            }
            String name = OutputSanitizer.text(proposal.name(), NAME_MAX * 2).replaceAll("\\s+", " ");
            String key = normalize(name);
            if (name.length() > NAME_MAX || !ALLOWED_NAME.matcher(name).matches() || key.length() < 2
                    || GENERIC.contains(key) || !seen.add(key)) {
                continue;
            }
            List<String> cited = OutputSanitizer.texts(proposal.sourceIds(), 6, 8).stream()
                    .map(id -> id.toUpperCase(Locale.ROOT)).filter(sourceText::containsKey).distinct().toList();
            if (cited.stream().noneMatch(id -> sourceText.get(id).contains(key))) {
                seen.remove(key);
                continue;
            }
            kept.add(new Proposal(name, OutputSanitizer.text(proposal.description(), DESCRIPTION_MAX), cited));
        }
        return kept;
    }

    /** Lower-case letters and digits only, so "Payment Service", "payment-service" and "PaymentService" match. */
    static String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]", "");
    }
}
