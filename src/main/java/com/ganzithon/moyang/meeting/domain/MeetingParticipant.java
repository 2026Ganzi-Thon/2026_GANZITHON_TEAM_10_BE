package com.ganzithon.moyang.meeting.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;

/** 모임 참여자. 방장도 만들 때 함께 저장한다(x/정원과 내 모임에 포함되도록). */
@Entity
@Table(name = "meeting_participants",
        uniqueConstraints = @UniqueConstraint(name = "uk_meeting_user", columnNames = {"meetingId", "userId"}),
        indexes = @Index(name = "idx_participant_user", columnList = "userId"))
public class MeetingParticipant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private long meetingId;

    @Column(nullable = false)
    private long userId;

    @Column(nullable = false, length = 30)
    private String meetingNickname;

    @Column(nullable = false, updatable = false)
    private LocalDateTime joinedAt;

    /** 신청을 취소한 시각. null이면 참여 중. 취소해도 행은 남겨 같은 모임에 다시 신청하지 못하게 한다. */
    private LocalDateTime canceledAt;

    /** 예약 모임에 실제로 입장한 시각. null이면 아직 입장하지 않았다(시작 판단에 쓴다). */
    private LocalDateTime enteredAt;

    /** '대화 종료'에 동의한 시각. null이면 아직 동의하지 않았다. 참여자 전원이 동의하면 모임이 종료된다. */
    private LocalDateTime endAgreedAt;

    protected MeetingParticipant() {}

    public MeetingParticipant(long meetingId, long userId, String meetingNickname, LocalDateTime joinedAt) {
        this.meetingId = meetingId;
        this.userId = userId;
        this.meetingNickname = meetingNickname;
        this.joinedAt = joinedAt;
    }

    public Long getId() { return id; }
    public long getMeetingId() { return meetingId; }
    public long getUserId() { return userId; }
    public String getMeetingNickname() { return meetingNickname; }
    public LocalDateTime getJoinedAt() { return joinedAt; }
    public LocalDateTime getCanceledAt() { return canceledAt; }

    public LocalDateTime getEnteredAt() { return enteredAt; }

    public boolean isActive() { return canceledAt == null; }

    public boolean hasEntered() { return enteredAt != null; }

    public LocalDateTime getEndAgreedAt() { return endAgreedAt; }

    public boolean hasAgreedEnd() { return endAgreedAt != null; }

    /** 종료 동의. 이미 동의했으면 처음 시각을 유지한다(여러 번 눌러도 1번). */
    public void agreeEnd(LocalDateTime now) {
        if (endAgreedAt == null) {
            this.endAgreedAt = now;
        }
    }

    /** 입장 기록. 이미 입장했으면 처음 시각을 유지한다(재입장해도 순서가 바뀌지 않게). */
    public void enter(LocalDateTime now) {
        if (enteredAt == null) {
            this.enteredAt = now;
        }
    }

    public void cancel(LocalDateTime now) {
        this.canceledAt = now;
    }
}
