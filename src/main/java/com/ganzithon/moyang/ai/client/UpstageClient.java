package com.ganzithon.moyang.ai.client;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

/** Upstage solar-pro4 (OpenAI 호환 API). API 키는 환경변수 UPSTAGE_API_KEY로만 받는다. */
@Component
public class UpstageClient implements AiClient {
    private final RestClient http;
    private final String apiKey;
    private final String model;

    public UpstageClient(@Value("${moyang.upstage.api-key:}") String apiKey,
                         @Value("${moyang.upstage.base-url:https://api.upstage.ai/v1}") String baseUrl,
                         @Value("${moyang.upstage.model:solar-pro4}") String model,
                         @Value("${moyang.upstage.timeout-seconds:30}") int timeoutSeconds) {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(timeoutSeconds));
        this.http = RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
        this.apiKey = apiKey;
        this.model = model;
    }

    @Override
    public String completeJson(String system, String user, String schemaName, Map<String, Object> schema) {
        if (apiKey.isBlank()) {
            throw new IllegalStateException("UPSTAGE_API_KEY is not set");
        }
        // response_format 없이는 JSON을 10번 중 1번만 지킨다 (PoC) → json_schema 강제 필수
        Map<String, Object> body = Map.of(
                "model", model,
                "messages", List.of(
                        Map.of("role", "system", "content", system),
                        Map.of("role", "user", "content", user)),
                "response_format", Map.of(
                        "type", "json_schema",
                        "json_schema", Map.of("name", schemaName, "strict", true, "schema", schema)));
        JsonNode response = http.post()
                .uri("/chat/completions")
                .header("Authorization", "Bearer " + apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(JsonNode.class);
        if (response == null) {
            throw new IllegalStateException("empty AI response");
        }
        return response.path("choices").path(0).path("message").path("content").asString();
    }
}
