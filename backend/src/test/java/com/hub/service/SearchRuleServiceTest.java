package com.hub.service;

import com.hub.config.HubProperties;
import com.hub.repository.SearchRuleRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SearchRuleServiceTest {
    @TempDir Path temp;

    @Test
    void configuredAliasSelectsFilenamePatternsWithoutJavaHardcoding() throws Exception {
        Path rules = temp.resolve("search-rules.yml");
        Files.writeString(rules, """
                rules:
                  - name: "기획서"
                    aliases: ["기획서", "PRD"]
                    patterns: ["*PRD*", "*기획서*"]
                    mode: "smart"
                """);
        SearchRuleRepository repository = mock(SearchRuleRepository.class);
        when(repository.list(1L, true)).thenReturn(List.of());
        SearchRuleService service = new SearchRuleService(properties(rules), repository);
        var match = service.match(1L, "PRD에서 모바일 로그인 기준 찾아줘").orElseThrow();
        assertEquals("기획서", match.name());
        assertTrue(match.patterns().contains("*PRD*"));
    }

    @Test
    void referenceFileCanInferSearchWordsWhenAdminLeavesAliasesEmpty() throws Exception {
        Path rules = temp.resolve("search-rules.yml");
        Files.writeString(rules, """
                rules:
                  - name: "주간 업무보고 기준 자료"
                    targetFile: "주간업무보고_양식.xlsx"
                    mode: "smart"
                """);
        SearchRuleRepository repository = mock(SearchRuleRepository.class);
        when(repository.list(1L, true)).thenReturn(List.of());
        SearchRuleService service = new SearchRuleService(properties(rules), repository);
        var match = service.match(1L, "지난주 주간 업무보고 찾아줘").orElseThrow();
        assertEquals("주간업무보고_양식.xlsx", match.targetFile());
    }

    private static HubProperties properties(Path rules) {
        return new HubProperties("./data", "http://127.0.0.1:8000", true,
          "./local-reader", rules.toString(), 12000, 2, 3, 16, 30000,
                "", "", "", "", "", "", "");
    }

    @Test
    void matchesDecomposedKoreanAliasAfterUnicodeNormalization() throws Exception {
        Path rules = temp.resolve("unicode-search-rules.yml");
        Files.writeString(rules, """
                rules:
                  - name: "기획서"
                    aliases: ["기획서"]
                    patterns: ["*기획서*"]
                    mode: "smart"
                """, java.nio.charset.StandardCharsets.UTF_8);
        SearchRuleRepository repository = mock(SearchRuleRepository.class);
        when(repository.list(1L, true)).thenReturn(List.of());
        SearchRuleService service = new SearchRuleService(properties(rules), repository);
        String decomposed = Normalizer.normalize("기획서 찾아줘", Normalizer.Form.NFD);
        var match = service.match(1L, decomposed).orElseThrow();
        assertEquals("기획서", match.name());
    }
}
