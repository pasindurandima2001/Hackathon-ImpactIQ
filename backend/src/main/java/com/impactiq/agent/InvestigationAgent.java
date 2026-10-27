package com.impactiq.agent;

import java.time.Instant;
import java.util.List;

/**
 * One specialist in the investigation workflow with a typed input and output contract.
 *
 * @param <I> input contract
 * @param <O> validated output contract
 */
public interface InvestigationAgent<I, O> {

    /** Stable identifier (also the ADK agent name, so letters, digits and underscores only). */
    String name();

    String displayName();

    AgentOutcome<O> run(I input, Instant deadline);

    /**
     * @param warnings server-side notes such as citations removed because they referenced unknown evidence
     */
    record AgentOutcome<O>(O output, int attempts, List<String> warnings) {
    }
}
