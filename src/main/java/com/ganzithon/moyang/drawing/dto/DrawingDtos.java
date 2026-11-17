package com.ganzithon.moyang.drawing.dto;

import java.time.OffsetDateTime;

/** 그림 API 응답 형식 (노션 API 명세서 기준). imageUrl은 인증이 필요한 상대경로 /api/drawings/{id}/image. */
public final class DrawingDtos {
    private DrawingDtos() {}

    public record DrawingUploadResponse(long drawingId, long recordId, String imageUrl, String inputType,
                                        Long topicId, String note, OffsetDateTime createdAt) {}

    public record TopicView(long topicId, String title) {}

    public record DrawingInfoResponse(long drawingId, long recordId, String imageUrl, String inputType,
                                      TopicView topic, String note, Long conversationId,
                                      OffsetDateTime createdAt) {}
}
