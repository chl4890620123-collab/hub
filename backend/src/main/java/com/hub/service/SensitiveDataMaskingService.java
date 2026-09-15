// STT transcripts can carry personal numbers straight from what a speaker read aloud. This masks
// them before the text ever reaches storage, search indexing, or the AI analysis call.
package com.hub.service;

import com.hub.repository.SensitiveTermRepository;
import com.hub.util.Hashing;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.regex.Pattern;

@Service
public class SensitiveDataMaskingService {
    private static final Logger log = LoggerFactory.getLogger(SensitiveDataMaskingService.class);

    private static final Pattern EMAIL = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
    // Resident registration number: 990101-1234567, or the same 13 digits run together.
    private static final Pattern RESIDENT_ID = Pattern.compile("\\b\\d{6}-\\d{7}\\b|\\b\\d{13}\\b");
    // Card numbers: 4-4-4-4 (Visa/Master/local) and 4-6-5 (Amex-style), dash/space optional.
    private static final Pattern CARD_NUMBER = Pattern.compile(
            "\\b\\d{4}[- ]?\\d{4}[- ]?\\d{4}[- ]?\\d{4}\\b|\\b\\d{4}[- ]?\\d{6}[- ]?\\d{5}\\b");
    // Korean mobile and landline numbers, dash/space optional.
    private static final Pattern PHONE_NUMBER = Pattern.compile("\\b0\\d{1,2}[- ]?\\d{3,4}[- ]?\\d{4}\\b");

    private final byte[] key;
    private final SensitiveTermRepository terms;

    public SensitiveDataMaskingService(@Value("${hub.stt-pii-hash-key:}") String configuredKey,
                                       SensitiveTermRepository terms) {
        this.terms = terms;
        if (configuredKey == null || configuredKey.isBlank()) {
            this.key = new byte[32];
            new SecureRandom().nextBytes(this.key);
            log.warn("hub.stt-pii-hash-key (HUB_STT_PII_HASH_KEY) is not set. Using a random key for this run only - "
                    + "masked values will not match across restarts. Set it in .env for a stable, admin-controlled key.");
        } else {
            this.key = configuredKey.getBytes(StandardCharsets.UTF_8);
        }
    }

    /**
     * Replaces admin-registered words/values, resident-registration numbers, card numbers, phone
     * numbers and emails with a one-way keyed hash token. The same value always redacts to the
     * same token (so repeats of the same value are still visible as "the same thing"), but the
     * original text cannot be recovered from the token without the key.
     */
    public String mask(String text) {
        if (text == null || text.isBlank()) return text;
        String masked = text;
        // Admin-registered terms first and longest-first, so a whole registered phrase is redacted
        // as one CUSTOM token before the built-in patterns can chew into part of it.
        for (SensitiveTermRepository.SensitiveTerm term : terms.list()) {
            if (term.term() == null || term.term().isBlank()) continue;
            if (masked.contains(term.term())) masked = masked.replace(term.term(), redact("CUSTOM", term.term()));
        }
        masked = EMAIL.matcher(masked).replaceAll(match -> redact("EMAIL", match.group()));
        masked = RESIDENT_ID.matcher(masked).replaceAll(match -> redact("RRN", match.group()));
        masked = CARD_NUMBER.matcher(masked).replaceAll(match -> redact("CARD", match.group()));
        masked = PHONE_NUMBER.matcher(masked).replaceAll(match -> redact("PHONE", match.group()));
        return masked;
    }

    private String redact(String label, String original) {
        return "[REDACTED:" + label + ":" + Hashing.hmacSha256Hex(key, original).substring(0, 12) + "]";
    }
}
