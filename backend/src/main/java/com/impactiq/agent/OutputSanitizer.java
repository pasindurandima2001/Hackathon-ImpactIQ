package com.impactiq.agent;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Server-side normalisation of agent output: length caps, enum checks and citation verification. */
public final class OutputSanitizer {

    private OutputSanitizer() {
    }

    public static String text(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        String trimmed = value.strip();
        return trimmed.length() <= maxLength ? trimmed : trimmed.substring(0, maxLength);
    }

    public static String requiredText(String value, int maxLength, String field) {
        String result = text(value, maxLength);
        if (result.isEmpty()) {
            throw new AgentException(AgentException.Category.INVALID_OUTPUT, "Agent output is missing " + field);
        }
        return result;
    }

    public static List<String> texts(List<String> values, int maxItems, int maxLength) {
        if (values == null) {
            return List.of();
        }
        return values.stream()
                .map(value -> text(value, maxLength))
                .filter(value -> !value.isEmpty())
                .distinct()
                .limit(maxItems)
                .toList();
    }

    public static String oneOf(String value, Collection<String> allowed, String fallback) {
        if (value == null) {
            return fallback;
        }
        String normalized = value.strip().toUpperCase(Locale.ROOT).replace(' ', '_');
        return allowed.contains(normalized) ? normalized : fallback;
    }

    public static int clamp(Integer value, int minimum, int maximum, int fallback) {
        if (value == null) {
            return fallback;
        }
        return Math.max(minimum, Math.min(maximum, value));
    }

    /** Keeps only IDs present in the evidence catalog; unknown IDs are reported in {@code unverified}. */
    public static List<String> citations(List<String> ids, Set<String> knownIds, List<String> unverified) {
        if (ids == null) {
            return List.of();
        }
        Set<String> kept = new LinkedHashSet<>();
        for (String id : ids) {
            if (id == null || id.isBlank()) {
                continue;
            }
            String normalized = id.strip().toUpperCase(Locale.ROOT);
            if (knownIds.contains(normalized)) {
                kept.add(normalized);
            } else if (!unverified.contains(normalized)) {
                unverified.add(normalized.length() > 12 ? normalized.substring(0, 12) : normalized);
            }
        }
        return new ArrayList<>(kept);
    }
}
