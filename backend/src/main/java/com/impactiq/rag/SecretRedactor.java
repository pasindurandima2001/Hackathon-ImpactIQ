package com.impactiq.rag;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Removes common credential formats from untrusted text before it is stored or sent to a model.
 * Best-effort: it reduces exposure but is not a guarantee that no secret remains.
 */
@Component
public class SecretRedactor {

    public static final String MASK = "[REDACTED]";

    private record Rule(Pattern pattern, String replacement) {
    }

    private static final List<Rule> RULES = List.of(
            new Rule(Pattern.compile("-----BEGIN [A-Z ]*PRIVATE KEY-----[\\s\\S]*?-----END [A-Z ]*PRIVATE KEY-----"),
                    "[REDACTED PRIVATE KEY]"),
            new Rule(Pattern.compile("\\b(?:AKIA|ASIA)[0-9A-Z]{16}\\b"), MASK),
            new Rule(Pattern.compile("\\bAIza[0-9A-Za-z_\\-]{35}\\b"), MASK),
            new Rule(Pattern.compile("\\b(?:gh[pousr]_[A-Za-z0-9]{36,}|github_pat_[A-Za-z0-9_]{22,})\\b"), MASK),
            new Rule(Pattern.compile("\\bxox[baprs]-[A-Za-z0-9-]{10,}\\b"), MASK),
            new Rule(Pattern.compile("\\b(?:sk|rk|pk)_(?:live|test)_[A-Za-z0-9]{16,}\\b"), MASK),
            new Rule(Pattern.compile("\\bsk-[A-Za-z0-9_\\-]{20,}\\b"), MASK),
            new Rule(Pattern.compile("\\beyJ[A-Za-z0-9_-]{8,}\\.[A-Za-z0-9_-]{8,}\\.[A-Za-z0-9_-]{8,}\\b"), MASK),
            new Rule(Pattern.compile("(?i)\\b(bearer|basic)\\s+[A-Za-z0-9._~+/=\\-]{12,}"), "$1 " + MASK),
            // scheme://user:password@host -> keep user and host, drop password
            new Rule(Pattern.compile("(?i)\\b([a-z][a-z0-9+.\\-]*://[^:/\\s@]+:)[^@\\s/]+(@)"), "$1" + MASK + "$2"),
            new Rule(Pattern.compile("(?i)\\b(password|passwd|pwd|secret|client[_-]?secret|api[_-]?key|apikey|access[_-]?key|"
                    + "secret[_-]?key|access[_-]?token|auth[_-]?token|refresh[_-]?token|private[_-]?key|token)"
                    + "(\\s*[\"']?\\s*[:=]\\s*[\"']?)([^\\s\"',;&<>]{3,})"), "$1$2" + MASK)
    );

    public RedactionResult redact(String text) {
        if (text == null || text.isEmpty()) {
            return new RedactionResult(text == null ? "" : text, 0);
        }
        String current = text;
        int count = 0;
        for (Rule rule : RULES) {
            Matcher matcher = rule.pattern().matcher(current);
            StringBuilder output = new StringBuilder(current.length());
            boolean found = false;
            while (matcher.find()) {
                if (matcher.group().contains(MASK)) {
                    continue;
                }
                found = true;
                count++;
                matcher.appendReplacement(output, rule.replacement());
            }
            if (found) {
                matcher.appendTail(output);
                current = output.toString();
            }
        }
        return new RedactionResult(current, count);
    }

    public record RedactionResult(String text, int redactionCount) {
    }
}
