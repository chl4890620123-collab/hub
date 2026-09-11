package com.hub.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SearchQueryRouterTest {
    private final SearchQueryRouter router = new SearchQueryRouter();

    @Test
    void exactFilenameUsesFilenameFirstPlan() {
        SearchQueryPlan plan = router.route("주간업무보고_0906.xlsx 찾아줘");
        assertEquals(SearchQueryPlan.Intent.EXACT_FILE, plan.intent());
        assertEquals("주간업무보고_0906.xlsx", plan.exactFilename());
        assertEquals("주간업무보고_0906.xlsx", plan.searchText());
        assertTrue(plan.lexicalWeight() > plan.semanticWeight());
    }

    @Test
    void naturalLanguageExtractsDateAuthorAndMeetingType() {
        SearchQueryPlan plan = router.route("지난주 김대리가 작성한 로그인 관련 회의록 찾아줘");
        assertEquals(SearchQueryPlan.Intent.FILTERED, plan.intent());
        assertEquals("김대리", plan.author());
        assertTrue(plan.hasDateFilter());
        assertTrue(plan.sourceTypes().isEmpty()); // 회의록은 업로드 문서일 수도 있으므로 hard source filter로 제한하지 않는다.
        assertTrue(plan.searchText().contains("로그인"));
        assertFalse(plan.searchText().contains("지난주"));
    }

    @Test
    void absoluteKoreanDateBecomesOneDayFilter() {
        SearchQueryPlan plan = router.route("9월 5일 로그인 회의록 찾아줘");
        assertTrue(plan.hasDateFilter());
        assertEquals(plan.fromInclusive().plusDays(1).toInstant(), plan.toExclusive().toInstant());
        assertFalse(plan.searchText().contains("9월 5일"));
    }

    @Test
    void templateQuestionBoostsTemplateChannel() {
        SearchQueryPlan plan = router.route("주간 업무보고랑 같은 양식 찾아줘");
        assertEquals(SearchQueryPlan.Intent.TEMPLATE, plan.intent());
        assertTrue(plan.templateWeight() > plan.semanticWeight());
    }

    @Test
    void explicitRecordedMeetingCanUseTranscriptSourceFilter() {
        SearchQueryPlan plan = router.route("지난주 회의 녹음에서 로그인 얘기 찾아줘");
        assertTrue(plan.sourceTypes().contains("MEETING_TRANSCRIPT"));
    }

    @Test
    void ordinaryIssueWordDoesNotForceGithub() {
        SearchQueryPlan plan = router.route("모바일 로그인 이슈 관련 자료 찾아줘");
        assertFalse(plan.sourceTypes().contains("GITHUB"));
    }
}
