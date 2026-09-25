package com.hub.controller;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ApiExceptionHandlerTest {
    private final ApiExceptionHandler handler = new ApiExceptionHandler();

    @Test
    void internalEnglishProcessingMessageIsNotExposed() {
        Map<String, Object> body = handler.state(
                new IllegalStateException("Processing job id was not generated")
        );

        assertEquals("PROCESSING_FAILED", body.get("error"));
        assertEquals("처리 중 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.", body.get("message"));
    }

    @Test
    void intentionalKoreanProcessingMessageIsPreserved() {
        Map<String, Object> body = handler.state(
                new IllegalStateException("Google Drive 자료를 불러오지 못했습니다.")
        );

        assertEquals("Google Drive 자료를 불러오지 못했습니다.", body.get("message"));
    }
}
