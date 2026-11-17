package com.ganzithon.moyang.ai.dto;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;

/** AI 대화 API 요청 · 응답 형식 (노션 API 명세서 기준). */
public final class ConversationDtos {
    private ConversationDtos() {}

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    /** 응답 시각은 한국 시간(+09:00), 초 단위. */
    public static OffsetDateTime kst(Instant instant) {
        return instant.truncatedTo(ChronoUnit.SECONDS).atZone(KST).toOffsetDateTime();
    }

    public record StartRequest(Long drawingId) {}

    /** type 없이 useWool만 보내면 '양털 1개로 더 이야기하기' (답은 이미 보낸 상태). */
    public record MessageRequest(String type, String content, Boolean useWool) {}

    public record EndRequest(String status) {}

    public record QuestionView(long messageId, String content, int order) {}

    public record SupportNotice(String message, String actionLabel, String phone) {
        public static final SupportNotice DEFAULT =
                new SupportNotice("혼자 감당하지 않아도 괜찮아요.", "조금 더 전문적인 상담 받기", "109");
    }

    public record StartResponse(long conversationId, long drawingId, long recordId, String status,
                                QuestionView question, int freeQuestions, boolean fallback,
                                SupportNotice supportNotice) {}

    public record ReplyResponse(long conversationId, QuestionView question, int remainingFreeQuestions,
                                boolean isLastQuestion, boolean canExtend, int extendCost, int woolUsed,
                                boolean fallback, SupportNotice supportNotice) {}

    public record DrawingView(long drawingId, String imageUrl, String topicTitle, String note) {}

    public record CardView(String whatISaid, String thoughts, String todaysStory) {}

    public record EndResponse(long conversationId, long recordId, String status, DrawingView drawing, CardView card,
                              boolean fallback) {}

    public record MessageView(long messageId, String role, String type, String content, int order,
                              OffsetDateTime createdAt) {}

    public record DetailResponse(long conversationId, long drawingId, long recordId, String status,
                                 List<MessageView> messages, int remainingFreeQuestions, boolean canExtend,
                                 DrawingView drawing, CardView card, boolean fallback) {}

    public record InProgressItem(long conversationId, long drawingId, long recordId, String imageUrl,
                                 String topicTitle, int remainingFreeQuestions, OffsetDateTime startedAt) {}

    public record InProgressResponse(InProgressItem conversation) {}

    public record MeetingCardResponse(long meetingId, long recordId, DrawingView drawing, CardView card,
                                      boolean fallback) {}
}
