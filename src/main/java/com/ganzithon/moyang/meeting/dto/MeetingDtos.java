package com.ganzithon.moyang.meeting.dto;

import java.time.OffsetDateTime;
import java.util.List;

public final class MeetingDtos {
    private MeetingDtos() {}

    /** 목록 한 칸. 예약 모임은 startAt, 실시간 모임은 startAt이 null. */
    public record MeetingSummary(long meetingId, String type, String title, String summary, String category,
                                 OffsetDateTime startAt, int capacity, int participantCount, String status,
                                 boolean joined) {}

    /** 값 검사는 서비스에서 한다(오류 코드를 명세대로 나누기 위해). 실시간(LIVE)은 startAt을 보내지 않는다. */
    public record CreateMeetingRequest(String type, String title, String description, String category,
                                       OffsetDateTime startAt, Integer capacity, String meetingNickname) {}

    public record CreateMeetingResponse(long meetingId, String status, int participantCount, int sheepUsed,
                                        int sheepBalance) {}

    public record JoinMeetingRequest(String meetingNickname) {}

    public record JoinMeetingResponse(long meetingId, int participantCount, int capacity, String status,
                                      String meetingNickname, int sheepUsed, int sheepBalance) {}

    /** 신청 취소 결과. refunded가 true면 양털 1개가 돌아왔다. */
    public record LeaveMeetingResponse(long meetingId, int participantCount, boolean refunded, int sheepRefunded,
                                       int sheepBalance) {}

    /** 방장 취소 결과. refundedParticipantCount는 환불받은 참여자 수, hostRefunded는 방장 생성비 환불 여부. */
    public record CancelMeetingResponse(long meetingId, String status, int refundedParticipantCount,
                                        boolean hostRefunded, int sheepBalance) {}

    public record MeetingListResponse(Long recommendedMeetingId, List<MeetingSummary> meetings) {}

    /** 내 모임 한 칸. 목록 칸에 내가 방장인지와 모임 닉네임이 더해진다. */
    public record MyMeetingItem(long meetingId, String type, String title, String summary, String category,
                                OffsetDateTime startAt, int capacity, int participantCount, String status,
                                boolean isHost, String myMeetingNickname) {}

    public record MyMeetingsResponse(List<MyMeetingItem> meetings) {}

    public record MeetingDetailResponse(long meetingId, String type, String title, String description,
                                        String category, List<String> tags, String topic,
                                        OffsetDateTime startAt, OffsetDateTime endAt, int capacity,
                                        int minParticipants, int participantCount, String status, boolean joined,
                                        String myMeetingNickname, List<String> steps, List<String> recommendedFor,
                                        long hostUserId, boolean isHost, boolean canStart, boolean canSkip,
                                        OffsetDateTime enterOpenAt, OffsetDateTime cancelRefundUntil,
                                        String cancelReason, String phase) {}

    /** 채팅 보내기 요청. type은 TEXT 또는 DRAWING. DRAWING은 drawingId가 필수이고 content는 그림 설명(선택). */
    public record SendMessageRequest(String type, String content, Long drawingId) {}

    public record SendMessageResponse(long messageId, OffsetDateTime createdAt) {}

    /** 채팅 한 줄. SYSTEM은 senderNickname이 null이고, DRAWING만 imageUrl이 있다. 응답에는 userId를 넣지 않는다. */
    public record ChatMessageItem(long messageId, String type, String senderNickname,
                                  String content, String imageUrl, boolean mine, OffsetDateTime createdAt) {}

    /** 채팅 폴링 응답: 모임 진행 정보와 새 메시지. 시작 전에는 phase · currentStep이 null. */
    public record ChatPollResponse(String status, String phase, Integer currentStep, String topic,
                                   int submittedDrawingCount, int participantCount, int endAgreedCount,
                                   boolean endAgreed, OffsetDateTime endAt, OffsetDateTime deleteAt, List<ChatMessageItem> messages) {}

    /** 대화 종료 동의 결과. 전원이 동의하면 status=ENDED. */
    public record EndMeetingResponse(long meetingId, int endAgreedCount, int participantCount, String status) {}

    /** 다음 단계로 넘긴 결과. topic은 모임 주제. */
    public record PhaseNextResponse(long meetingId, String phase, String topic) {}

    /** 실시간 모임 시작 결과. 시작하면 status=IN_PROGRESS, phase=INTRO. */
    public record StartMeetingResponse(long meetingId, String status, String phase, int participantCount) {}

    /** 예약 모임 입장 결과. enteredCount는 지금까지 입장한 사람 수, 2명이 모여 시작됐다면 status=IN_PROGRESS. */
    public record EnterMeetingResponse(long meetingId, String status, String phase, int enteredCount,
                                       OffsetDateTime startAt) {}
}
