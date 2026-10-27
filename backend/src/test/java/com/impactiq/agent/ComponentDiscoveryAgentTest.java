package com.impactiq.agent;

import com.impactiq.agent.ComponentDiscoveryAgent.Proposal;
import com.impactiq.agent.ComponentDiscoveryAgent.Request;
import com.impactiq.agent.ComponentDiscoveryAgent.Source;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ComponentDiscoveryAgentTest {

    private static final Request REQUEST = new Request("Checkout Platform", List.of("Billing"), List.of(
            new Source("S1", "architecture.md", "The Payment Service calls the fraud-checker. Billing sends invoices."),
            new Source("S2", "mobile.md", "The Android App talks to the API gateway.")));

    @Test
    void keepsOnlyNamesThatOccurInACitedSource() {
        List<Proposal> kept = ComponentDiscoveryAgent.validate(List.of(
                new Proposal("PaymentService", "Charges cards.", List.of("s1")),
                new Proposal("Fraud Checker", "Scores risk.", List.of("S1")),
                new Proposal("Android App", "Mobile client.", List.of("S2")),
                new Proposal("Inventory Service", "Invented.", List.of("S1")),
                new Proposal("API Gateway", "Wrong citation.", List.of("S1")),
                new Proposal("Notifications", "Unknown source.", List.of("S9"))
        ), REQUEST);

        assertEquals(List.of("PaymentService", "Fraud Checker", "Android App"), kept.stream().map(Proposal::name).toList());
        assertEquals(List.of("S1"), kept.get(0).sourceIds());
    }

    @Test
    void dropsExistingGenericProjectDuplicateAndMalformedNames() {
        List<Proposal> kept = ComponentDiscoveryAgent.validate(java.util.Arrays.asList(
                new Proposal("billing", "Already exists.", List.of("S1")),
                new Proposal("API", "Generic.", List.of("S2")),
                new Proposal("Checkout Platform", "The project itself.", List.of("S1")),
                new Proposal("Payment Service", "First.", List.of("S1")),
                new Proposal("payment-service", "Duplicate spelling.", List.of("S1")),
                new Proposal("<b>Android App</b>", "Markup.", List.of("S2")),
                new Proposal("  Android   App ", "Whitespace is collapsed.", List.of("S2")),
                null
        ), REQUEST);

        assertEquals(List.of("Payment Service", "Android App"), kept.stream().map(Proposal::name).toList());
    }

    @Test
    void normalisationIgnoresCaseSpacingAndPunctuation() {
        assertEquals("paymentservice", ComponentDiscoveryAgent.normalize("Payment-Service"));
        assertTrue(ComponentDiscoveryAgent.validate(null, REQUEST).isEmpty());
    }
}
