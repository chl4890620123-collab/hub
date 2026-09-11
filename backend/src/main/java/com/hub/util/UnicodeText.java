package com.hub.util;

import java.text.Normalizer;

/**
 * Keeps Korean and other Unicode text in NFC so macOS-style decomposed filenames
 * compare consistently with Windows/browser/API input.
 */
public final class UnicodeText {
    private UnicodeText() {}

    public static String nfc(String value) {
        return value == null ? "" : Normalizer.normalize(value, Normalizer.Form.NFC);
    }

    public static String nfcNullable(String value) {
        return value == null ? null : Normalizer.normalize(value, Normalizer.Form.NFC);
    }
}
