package com.llmobservability.platform.inferencegateway.adapter.out.redaction;

import com.llmobservability.platform.inferencegateway.application.port.out.PiiRedactionResult;
import com.llmobservability.platform.inferencegateway.config.PiiRedactionProperties;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RegexPiiRedactionAdapterTest {

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private RegexPiiRedactionAdapter adapter(boolean enabled, String... categories) {
        PiiRedactionProperties props = new PiiRedactionProperties();
        props.setEnabled(enabled);
        if (categories.length > 0) {
            props.setCategories(List.of(categories));
        }
        return new RegexPiiRedactionAdapter(props);
    }

    private RegexPiiRedactionAdapter allEnabled() {
        return adapter(true, "EMAIL", "PHONE", "CREDIT_CARD", "SSN", "IP_ADDRESS", "AADHAAR");
    }

    // -------------------------------------------------------------------------
    // EMAIL
    // -------------------------------------------------------------------------

    @Test
    void emailDetectedAndReplaced() {
        PiiRedactionResult result = adapter(true, "EMAIL").scan("Contact me at john.doe@example.com for details.");
        assertThat(result.hasRedactions()).isTrue();
        assertThat(result.detectedCategories()).containsExactly("EMAIL");
        assertThat(result.redactedContent()).isEqualTo("Contact me at [EMAIL] for details.");
    }

    @Test
    void multipleEmailsInOneMessage() {
        PiiRedactionResult result = adapter(true, "EMAIL").scan("From alice@foo.com to bob@bar.org");
        assertThat(result.detectedCategories()).containsExactly("EMAIL");
        assertThat(result.redactedContent()).isEqualTo("From [EMAIL] to [EMAIL]");
    }

    // -------------------------------------------------------------------------
    // PHONE
    // -------------------------------------------------------------------------

    @Test
    void phoneE164DetectedAndReplaced() {
        PiiRedactionResult result = adapter(true, "PHONE").scan("Call me at +919876543210 tomorrow.");
        assertThat(result.detectedCategories()).containsExactly("PHONE");
        assertThat(result.redactedContent()).contains("[PHONE]");
        assertThat(result.redactedContent()).doesNotContain("+919876543210");
    }

    @Test
    void phoneWithDashesDetected() {
        PiiRedactionResult result = adapter(true, "PHONE").scan("My number is 555-867-5309.");
        assertThat(result.hasRedactions()).isTrue();
        assertThat(result.detectedCategories()).containsExactly("PHONE");
        assertThat(result.redactedContent()).doesNotContain("555-867-5309");
    }

    // -------------------------------------------------------------------------
    // CREDIT_CARD
    // -------------------------------------------------------------------------

    @Test
    void validCreditCardDetectedAndReplaced() {
        // Luhn-valid Visa test number
        PiiRedactionResult result = adapter(true, "CREDIT_CARD").scan("My card is 4111111111111111 please charge it.");
        assertThat(result.detectedCategories()).containsExactly("CREDIT_CARD");
        assertThat(result.redactedContent()).contains("[CREDIT_CARD]");
        assertThat(result.redactedContent()).doesNotContain("4111111111111111");
    }

    @Test
    void invalidLuhnSequenceNotRedacted() {
        // Fails Luhn check — should NOT be redacted
        PiiRedactionResult result = adapter(true, "CREDIT_CARD").scan("The code is 4111111111111112 on the box.");
        assertThat(result.hasRedactions()).isFalse();
        assertThat(result.redactedContent()).contains("4111111111111112");
    }

    @Test
    void creditCardWithSpacesDetected() {
        // Common formatting: 4 groups of 4 digits
        PiiRedactionResult result = adapter(true, "CREDIT_CARD").scan("Card: 4111 1111 1111 1111");
        assertThat(result.detectedCategories()).containsExactly("CREDIT_CARD");
    }

    // -------------------------------------------------------------------------
    // SSN
    // -------------------------------------------------------------------------

    @Test
    void ssnDetectedAndReplaced() {
        PiiRedactionResult result = adapter(true, "SSN").scan("My SSN is 123-45-6789.");
        assertThat(result.detectedCategories()).containsExactly("SSN");
        assertThat(result.redactedContent()).isEqualTo("My SSN is [SSN].");
    }

    @Test
    void invalidSsnGroupNotRedacted() {
        // 000-xx-xxxx is excluded by the SSN pattern
        PiiRedactionResult result = adapter(true, "SSN").scan("Code 000-12-3456 is not an SSN.");
        assertThat(result.hasRedactions()).isFalse();
    }

    // -------------------------------------------------------------------------
    // IP_ADDRESS
    // -------------------------------------------------------------------------

    @Test
    void ipv4DetectedAndReplaced() {
        PiiRedactionResult result = adapter(true, "IP_ADDRESS").scan("Server at 192.168.1.100 is down.");
        assertThat(result.detectedCategories()).containsExactly("IP_ADDRESS");
        assertThat(result.redactedContent()).isEqualTo("Server at [IP_ADDRESS] is down.");
    }

    @Test
    void loopbackAddressDetected() {
        PiiRedactionResult result = adapter(true, "IP_ADDRESS").scan("localhost resolves to 127.0.0.1");
        assertThat(result.detectedCategories()).containsExactly("IP_ADDRESS");
    }

    // -------------------------------------------------------------------------
    // AADHAAR
    // -------------------------------------------------------------------------

    @Test
    void aadhaarDetectedAndReplaced() {
        PiiRedactionResult result = adapter(true, "AADHAAR").scan("Aadhaar number: 2345 6789 0123");
        assertThat(result.detectedCategories()).containsExactly("AADHAAR");
        assertThat(result.redactedContent()).isEqualTo("Aadhaar number: [AADHAAR]");
    }

    @Test
    void aadhaarWithDashesDetected() {
        PiiRedactionResult result = adapter(true, "AADHAAR").scan("ID: 2345-6789-0123");
        assertThat(result.detectedCategories()).containsExactly("AADHAAR");
    }

    // -------------------------------------------------------------------------
    // Multi-category
    // -------------------------------------------------------------------------

    @Test
    void multiCategoryMessageDetectsAllApplicable() {
        String message = "Email alice@example.com, SSN 123-45-6789, IP 10.0.0.1";
        PiiRedactionResult result = allEnabled().scan(message);
        assertThat(result.detectedCategories()).containsExactlyInAnyOrder("EMAIL", "SSN", "IP_ADDRESS");
        assertThat(result.redactedContent())
                .contains("[EMAIL]")
                .contains("[SSN]")
                .contains("[IP_ADDRESS]")
                .doesNotContain("alice@example.com")
                .doesNotContain("123-45-6789")
                .doesNotContain("10.0.0.1");
    }

    // -------------------------------------------------------------------------
    // Clean message (no PII)
    // -------------------------------------------------------------------------

    @Test
    void cleanMessageReturnsOriginalContentAndNoCategories() {
        String clean = "What is the capital of France?";
        PiiRedactionResult result = allEnabled().scan(clean);
        assertThat(result.hasRedactions()).isFalse();
        assertThat(result.detectedCategories()).isEmpty();
        assertThat(result.redactedContent()).isEqualTo(clean);
    }

    // -------------------------------------------------------------------------
    // Disabled adapter
    // -------------------------------------------------------------------------

    @Test
    void disabledAdapterReturnsOriginalContentUnchanged() {
        RegexPiiRedactionAdapter disabled = adapter(false);
        String content = "My email is admin@secret.com and SSN is 123-45-6789";
        PiiRedactionResult result = disabled.scan(content);
        assertThat(result.hasRedactions()).isFalse();
        assertThat(result.redactedContent()).isEqualTo(content);
    }

    // -------------------------------------------------------------------------
    // Specific category disabled
    // -------------------------------------------------------------------------

    @Test
    void specificCategoryDisabledLeavesItUnredacted() {
        // EMAIL disabled, SSN enabled
        RegexPiiRedactionAdapter emailOff = adapter(true, "SSN");
        String content = "Email me at user@host.com, SSN 234-56-7890";
        PiiRedactionResult result = emailOff.scan(content);
        assertThat(result.detectedCategories()).containsExactly("SSN");
        assertThat(result.redactedContent()).contains("user@host.com"); // email left as-is
        assertThat(result.redactedContent()).contains("[SSN]");
    }

    // -------------------------------------------------------------------------
    // Luhn algorithm unit tests
    // -------------------------------------------------------------------------

    @Test
    void luhnKnownValidNumbers() {
        // Standard Visa/MC test numbers
        assertThat(RegexPiiRedactionAdapter.luhn("4111111111111111")).isTrue();
        assertThat(RegexPiiRedactionAdapter.luhn("5500005555555559")).isTrue();
        assertThat(RegexPiiRedactionAdapter.luhn("378282246310005")).isTrue();
    }

    @Test
    void luhnKnownInvalidNumbers() {
        assertThat(RegexPiiRedactionAdapter.luhn("4111111111111112")).isFalse();
        assertThat(RegexPiiRedactionAdapter.luhn("1234567890123456")).isFalse();
    }

    @Test
    void luhnRejectsTooShortOrNullInput() {
        assertThat(RegexPiiRedactionAdapter.luhn(null)).isFalse();
        assertThat(RegexPiiRedactionAdapter.luhn("123")).isFalse();
    }
}
