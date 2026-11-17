package com.ganzithon.moyang.meeting.domain;

public final class MeetingEnums {
    private MeetingEnums() {}

    public enum MeetingType {
        /** 미리 약속한 모양(예약). startAt이 있다. */
        SCHEDULED,
        /** 지금 모이는 모양(실시간). startAt 없이 바로 대기실. */
        LIVE
    }

    /** RECRUITING(예약 대기) → OPEN(입장 가능) → IN_PROGRESS(진행 중) → ENDED(종료) / CANCELED(취소). 프론트는 이 값만 보고 화면을 정한다. */
    public enum MeetingStatus {
        RECRUITING, OPEN, IN_PROGRESS, ENDED, CANCELED
    }

    public enum CancelReason {
        HOST_CANCELED, NOT_ENOUGH_PARTICIPANTS
    }
}
