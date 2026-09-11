package com.hub.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TemplateSimilarityServiceTest {
    @Test
    void representativePartsUseBeginningMiddleAndEndWithoutDependingOnFilename() {
        List<String> parts = TemplateSimilarityService.representativeParts(List.of(
                "작성일 담당자 진행 업무 문제점 다음 주 계획",
                "중간 설명",
                "금주 실적 이슈 차주 계획"
        ));
        assertEquals(3, parts.size());
        assertTrue(parts.get(0).contains("작성일"));
        assertTrue(parts.get(2).contains("차주 계획"));
    }
}
