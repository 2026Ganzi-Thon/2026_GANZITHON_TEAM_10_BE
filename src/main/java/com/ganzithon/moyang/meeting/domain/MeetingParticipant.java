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

    public boolean isActive() { return canceledAt == null; }

    public void cancel(LocalDateTime now) {
        this.canceledAt = now;
    }
}
