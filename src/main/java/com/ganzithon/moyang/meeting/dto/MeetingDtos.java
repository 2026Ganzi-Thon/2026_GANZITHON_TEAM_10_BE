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

    /** 방장 취소 결과. refundedParticipants는 환불받은 참여자 수, hostRefunded는 방장 생성비 환불 여부. */
    public record CancelMeetingResponse(long meetingId, String status, int refundedParticipants,
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
                                        String cancelReason) {}
}
