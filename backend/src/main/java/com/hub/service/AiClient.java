package com.hub.service;

import com.hub.config.HubProperties;
import com.hub.dto.AiDtos;
import com.hub.util.HttpRequestFactories;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@Service
public class AiClient {
    private final RestClient client;

    public AiClient(HubProperties props, RestClient.Builder builder) {
        this.client = builder
                .baseUrl(props.aiBaseUrl())
                .requestFactory(HttpRequestFactories.create(Duration.ofSeconds(5), Duration.ofMinutes(10)))
                .build();
    }

    public AiDtos.AnalyzeResponse analyze(String text, String sourceDate) {
        return call(() -> client.post()
                .uri("/api/v1/analyze")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("text", text, "source_date", sourceDate == null ? "" : sourceDate))
                .retrieve()
                .body(AiDtos.AnalyzeResponse.class));
    }

    public AiDtos.RagResponse rag(String question, List<AiDtos.RagChunk> chunks) {
        return call(() -> client.post()
                .uri("/api/v1/rag")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("question", question, "chunks", chunks))
                .retrieve()
                .body(AiDtos.RagResponse.class));
    }

    /** E5 requires different prefixes for user queries and indexed passages. */
    public AiDtos.EmbedResponse embed(List<String> texts, String inputType) {
        String safeType = "query".equals(inputType) ? "query" : "passage";
        return call(() -> client.post()
                .uri("/api/v1/embed")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("texts", texts, "input_type", safeType))
                .retrieve()
                .body(AiDtos.EmbedResponse.class));
    }

    public AiDtos.ChangeResponse changes(String before, String after) {
        return call(() -> client.post()
                .uri("/api/v1/changes")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("before", before, "after", after))
                .retrieve()
                .body(AiDtos.ChangeResponse.class));
    }

    public AiDtos.ReviseResponse revise(String originalText, String meetingText) {
        return call(() -> client.post()
                .uri("/api/v1/revise")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("original_text", originalText, "meeting_text", meetingText))
                .retrieve()
                .body(AiDtos.ReviseResponse.class));
    }

    public String ocr(String filename, String contentType, byte[] data) {
        Map<?, ?> response = upload("/api/v1/ocr", filename, contentType, data, Map.class);
        Object text = response == null ? null : response.get("text");
        return text == null ? "" : text.toString();
    }

    public AiDtos.SttResponse stt(String filename, String contentType, byte[] data) {
        return upload("/api/v1/stt", filename, contentType, data, AiDtos.SttResponse.class);
    }

    private <T> T upload(String path, String filename, String contentType, byte[] data, Class<T> type) {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        ByteArrayResource resource = new ByteArrayResource(data) {
            @Override
            public String getFilename() {
                return filename;
            }
        };
        HttpHeaders partHeaders = new HttpHeaders();
        partHeaders.setContentType(safeMediaType(contentType));
        body.add("file", new HttpEntity<>(resource, partHeaders));
        return call(() -> client.post()
                .uri(path)
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(body)
                .retrieve()
                .body(type));
    }


    private MediaType safeMediaType(String contentType) {
        if (contentType == null || contentType.isBlank()) return MediaType.APPLICATION_OCTET_STREAM;
        try {
            return MediaType.parseMediaType(contentType);
        } catch (IllegalArgumentException ignored) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }

    private <T> T call(java.util.function.Supplier<T> supplier) {
        try {
            return supplier.get();
        } catch (RestClientException e) {
            throw new IllegalStateException("AI service request failed", e);
        }
    }
}
