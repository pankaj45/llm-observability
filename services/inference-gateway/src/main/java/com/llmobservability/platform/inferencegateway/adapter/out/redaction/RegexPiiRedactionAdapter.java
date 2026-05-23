package com.llmobservability.platform.inferencegateway.adapter.out.redaction;

import com.llmobservability.platform.inferencegateway.application.port.out.PiiRedactionPort;
import com.llmobservability.platform.inferencegateway.application.port.out.PiiRedactionResult;
import com.llmobservability.platform.inferencegateway.config.PiiRedactionProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Regex-based PII redaction adapter.
 *
 * <p>Applies a compiled {@link Pattern} per enabled PII category in order.
 * All non-overlapping matches within each category are replaced with the category
 * placeholder token (e.g. {@code [EMAIL]}). Categories are applied sequentially so
 * earlier replacements do not interfere with later patterns.
 *
 * <p>When redaction is disabled via {@link PiiRedactionProperties#isEnabled()},
 * the original content is returned unchanged with an empty category list.
 *
 * <p>This class is intentionally free of any Kafka, Redis, R2DBC, or WebClient
 * dependency. It is safe to use in unit tests without a Spring context.
 */
@Component
class RegexPiiRedactionAdapter implements PiiRedactionPort {

    /**
     * Ordered map of (category name → compiled pattern).
     * Only categories present in {@link PiiRedactionProperties#getCategories()} are included.
     */
    private static final Map<String, Pattern> ALL_PATTERNS = buildPatterns();

    private final PiiRedactionProperties properties;
    private final Map<String, Pattern> enabledPatterns;

    @Autowired
    RegexPiiRedactionAdapter(PiiRedactionProperties properties) {
        this.properties = properties;
        Set<String> enabled = Set.copyOf(properties.getCategories());
        Map<String, Pattern> active = new LinkedHashMap<>();
        ALL_PATTERNS.forEach((name, pattern) -> {
            if (enabled.contains(name)) {
                active.put(name, pattern);
            }
        });
        this.enabledPatterns = Map.copyOf(active);
    }

    @Override
    public PiiRedactionResult scan(String content) {
        if (!properties.isEnabled() || enabledPatterns.isEmpty()) {
            return new PiiRedactionResult(content, List.of());
        }

        String redacted = content;
        List<String> detected = new ArrayList<>();

        for (Map.Entry<String, Pattern> entry : enabledPatterns.entrySet()) {
            String category = entry.getKey();
            Pattern pattern = entry.getValue();
            String placeholder = "[" + category + "]";

            String next = pattern.matcher(redacted).replaceAll(m -> {
                // Luhn check for credit cards to reduce false positives
                if ("CREDIT_CARD".equals(category)) {
                    String digits = m.group().replaceAll("[^0-9]", "");
                    if (!luhn(digits)) {
                        return m.group(); // not a valid card — leave unchanged
                    }
                }
                return placeholder;
            });

            if (!next.equals(redacted)) {
                detected.add(category);
                redacted = next;
            }
        }

        return new PiiRedactionResult(redacted, List.copyOf(detected));
    }

    // -------------------------------------------------------------------------
    // Pattern definitions
    // -------------------------------------------------------------------------

    private static Map<String, Pattern> buildPatterns() {
        Map<String, Pattern> patterns = new LinkedHashMap<>();

        // EMAIL — local@domain.tld
        patterns.put("EMAIL", Pattern.compile(
                "[a-zA-Z0-9._%+\\-]+@[a-zA-Z0-9.\\-]+\\.[a-zA-Z]{2,}",
                Pattern.CASE_INSENSITIVE));

        // CREDIT_CARD — 13–19 digits optionally separated by spaces or dashes;
        // Luhn validity check is applied in scan() to reduce false positives
        patterns.put("CREDIT_CARD", Pattern.compile(
                "\\b(?:\\d[ \\-]?){13,19}\\b"));

        // SSN — US Social Security Number NNN-NN-NNNN
        patterns.put("SSN", Pattern.compile(
                "\\b(?!000|666|9\\d{2})\\d{3}-(?!00)\\d{2}-(?!0{4})\\d{4}\\b"));

        // IP_ADDRESS — IPv4 dotted-quad (no boundary check needed; dots serve as anchors)
        patterns.put("IP_ADDRESS", Pattern.compile(
                "\\b(?:(?:25[0-5]|2[0-4]\\d|[01]?\\d\\d?)\\.){3}(?:25[0-5]|2[0-4]\\d|[01]?\\d\\d?)\\b"));

        // AADHAAR — Indian 12-digit national ID, groups of 4 separated by optional space/dash
        patterns.put("AADHAAR", Pattern.compile(
                "\\b[2-9]\\d{3}[\\s\\-]?\\d{4}[\\s\\-]?\\d{4}\\b"));

        // PHONE — E.164 and common local formats (7–15 digits, optional +, spaces, dashes, parens)
        // Moved to the end because it is a very generic pattern that can accidentally
        // match SSN, AADHAAR, and invalid Credit Cards.
        patterns.put("PHONE", Pattern.compile(
                "(?<![\\d])(?:\\+?\\d[\\d\\s\\-.()]{6,}\\d)(?![\\d])"));

        return Map.copyOf(patterns);
    }

    // -------------------------------------------------------------------------
    // Luhn algorithm
    // -------------------------------------------------------------------------

    /**
     * Returns {@code true} if {@code digits} passes the Luhn check.
     * Only digits are expected in the input string.
     */
    static boolean luhn(String digits) {
        if (digits == null || digits.length() < 13 || digits.length() > 19) {
            return false;
        }
        int sum = 0;
        boolean doubleIt = false;
        for (int i = digits.length() - 1; i >= 0; i--) {
            int d = digits.charAt(i) - '0';
            if (doubleIt) {
                d *= 2;
                if (d > 9) d -= 9;
            }
            sum += d;
            doubleIt = !doubleIt;
        }
        return sum % 10 == 0;
    }
}
