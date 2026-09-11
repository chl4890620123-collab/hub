package com.hub.service;

import org.junit.jupiter.api.Test;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DocumentParserServiceTest {
    private final DocumentParserService parser = new DocumentParserService();

    @Test
    void readsKoreanUtf8WithoutMojibake() {
        String original = "한글 파일 검색과 원문 근거를 확인합니다.";
        assertEquals(original, parser.parse("업무보고.txt", original.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void readsLegacyKoreanWindowsTextAsMs949() {
        String original = "주간업무보고 담당자 김대리";
        assertEquals(original, parser.parse("주간보고.txt", original.getBytes(Charset.forName("MS949"))));
    }

    @Test
    void normalizesDecomposedKoreanToNfc() {
        String original = "기획서 검색";
        String decomposed = Normalizer.normalize(original, Normalizer.Form.NFD);
        String parsed = parser.parse("기획서.txt", decomposed.getBytes(StandardCharsets.UTF_8));
        assertEquals(original, parsed);
        assertTrue(Normalizer.isNormalized(parsed, Normalizer.Form.NFC));
    }
}
