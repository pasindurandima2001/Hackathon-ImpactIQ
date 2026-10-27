package com.impactiq.rag;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Flags instruction-like phrases in untrusted content. Flagged content is still passed to agents as quoted
 * data (never as instructions); the flag is surfaced in the report so reviewers know to treat it with care.
 */
@Component
public class PromptInjectionDetector {

    private static final List<Pattern> PATTERNS = List.of(
            Pattern.compile("(?i)\\b(ignore|disregard|forget|override)\\s+(all\\s+|any\\s+|the\\s+)?(previous|prior|above|earlier|system)\\s+"
                    + "(instructions?|prompts?|rules?|messages?|context)"),
            Pattern.compile("(?i)\\byou\\s+are\\s+now\\s+(a|an|the)\\b"),
            Pattern.compile("(?i)\\b(reveal|print|show|output)\\s+(your|the)\\s+(system\\s+prompt|instructions|api\\s*key|secrets?)"),
            Pattern.compile("(?i)\\bnew\\s+(system\\s+)?instructions?\\s*:"),
            Pattern.compile("(?i)</?\\s*(system|assistant|untrusted_data|instructions?)\\s*>"),
            Pattern.compile("(?i)\\b(mark|declare)\\s+(this|the)\\s+(root\\s+cause|hypothesis)\\s+(as\\s+)?confirmed"),
            Pattern.compile("(?i)\\bset\\s+(the\\s+)?(impact\\s+)?(score|priority|severity)\\s+to\\b")
    );

    public boolean isSuspicious(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        for (Pattern pattern : PATTERNS) {
            if (pattern.matcher(text).find()) {
                return true;
            }
        }
        return false;
    }
}
