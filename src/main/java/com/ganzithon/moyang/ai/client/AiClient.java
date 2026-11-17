package com.ganzithon.moyang.ai.client;

import java.util.Map;

public interface AiClient {
    /**
     * JSON 스키마를 강제해 호출하고, 모델이 돌려준 JSON 문자열을 반환한다.
     * 실패 · 타임아웃이면 RuntimeException을 던진다 (호출하는 쪽이 fallback 처리).
     */
    String completeJson(String system, String user, String schemaName, Map<String, Object> schema);
}
