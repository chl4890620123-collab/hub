package com.hub.service;

import com.hub.util.UnicodeText;
import org.apache.tika.Tika;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;

@Service
public class DocumentParserService {
    private static final Set<String> PLAIN_TEXT_EXTENSIONS = Set.of(
            "txt", "md", "csv", "tsv", "log", "json", "xml", "yaml", "yml"
    );
    private final Tika tika = new Tika();

    public String parse(String filename, byte[] bytes) {
        try {
            if (isPlainText(filename)) {
                String decoded = decodePlainText(bytes);
                if (decoded != null) return UnicodeText.nfc(decoded).strip();
            }
            String text = tika.parseToString(new ByteArrayInputStream(bytes));
            return UnicodeText.nfc(text == null ? "" : text).strip();
        } catch (Exception e) {
            throw new IllegalArgumentException("파일 내용을 읽지 못했습니다: " + filename, e);
        }
    }

    private static boolean isPlainText(String filename) {
        if (filename == null) return false;
        String lower = filename.toLowerCase(Locale.ROOT);
        int dot = lower.lastIndexOf('.');
        return dot >= 0 && dot + 1 < lower.length() && PLAIN_TEXT_EXTENSIONS.contains(lower.substring(dot + 1));
    }

    private static String decodePlainText(byte[] bytes) {
        if (bytes == null || bytes.length == 0) return "";
        if (startsWith(bytes, 0xEF, 0xBB, 0xBF)) {
            return new String(bytes, 3, bytes.length - 3, StandardCharsets.UTF_8);
        }
        if (startsWith(bytes, 0xFF, 0xFE)) {
            return new String(bytes, 2, bytes.length - 2, StandardCharsets.UTF_16LE);
        }
        if (startsWith(bytes, 0xFE, 0xFF)) {
            return new String(bytes, 2, bytes.length - 2, StandardCharsets.UTF_16BE);
        }

        String utf8 = decodeStrict(bytes, StandardCharsets.UTF_8);
        if (utf8 != null) return utf8;

        // Korean Windows files are still frequently saved as CP949/MS949. Accept them only as
        // a legacy import fallback; all Hub storage and API output remains UTF-8.
        String ms949 = decodeStrict(bytes, Charset.forName("MS949"));
        if (ms949 != null) return ms949;
        return decodeStrict(bytes, Charset.forName("EUC-KR"));
    }

    private static String decodeStrict(byte[] bytes, Charset charset) {
        try {
            CharBuffer chars = charset.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes));
            return chars.toString();
        } catch (CharacterCodingException ignored) {
            return null;
        }
    }

    private static boolean startsWith(byte[] bytes, int... prefix) {
        if (bytes.length < prefix.length) return false;
        for (int i = 0; i < prefix.length; i++) {
            if ((bytes[i] & 0xFF) != prefix[i]) return false;
        }
        return true;
    }
}
