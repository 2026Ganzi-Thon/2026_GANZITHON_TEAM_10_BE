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

    /** 진행 단계. INTRO(자기소개) → DRAWING(주제·그림) → SHARING(그림 이야기) → CLOSING(마무리). 시작하면 INTRO. */
    public enum MeetingPhase {
        INTRO, DRAWING, SHARING, CLOSING;

        public boolean isLast() {
            return this == CLOSING;
        }

        /** 다음 단계. 마지막 단계(CLOSING)에서는 부르면 안 된다(isLast로 먼저 확인). */
        public MeetingPhase next() {
            return values()[ordinal() + 1];
        }
    }

    /** 채팅 메시지 종류. TEXT 일반 채팅, DRAWING 그림 공유, SYSTEM 서버가 남기는 안내(참여자는 보낼 수 없다). */
    public enum MessageType {
        TEXT, DRAWING, SYSTEM
    }

    public enum CancelReason {
        HOST_CANCELED, NOT_ENOUGH_PARTICIPANTS
    }
}
