package com.ganzithon.moyang.meeting.domain;

import com.ganzithon.moyang.meeting.domain.MeetingEnums.CancelReason;
import com.ganzithon.moyang.meeting.domain.MeetingEnums.MeetingPhase;
import com.ganzithon.moyang.meeting.domain.MeetingEnums.MeetingStatus;
import com.ganzithon.moyang.meeting.domain.MeetingEnums.MeetingType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.LocalDateTime;

/** 그림 나눔 모임. 시각은 모두 한국 시간(KST) 기준 LocalDateTime으로 저장하고, 응답에서 +09:00을 붙인다. */
@Entity
@Table(name = "meetings")
public class Meeting {

    /** 예약 모임 입장이 열리는 시점 (시작 5분 전). */
    public static final Duration ENTER_OPEN_BEFORE = Duration.ofMinutes(5);
    /** 참여자 취소 환불 마감 (시작 10분 전). */
    public static final Duration REFUND_DEADLINE_BEFORE = Duration.ofMinutes(10);
    /** 예약 모임 시작 시각에 입장자가 모자랄 때 기다려 주는 시간. 시작 시각 + 2분이 넘으면 자동 취소한다. */
    public static final Duration START_WAIT = Duration.ofMinutes(2);
    /** 예약 모임 진행 시간(표시용). */
    public static final Duration SCHEDULED_LENGTH = Duration.ofMinutes(40);
    /** 종료된 모임 상세를 보여 주는 기간. */
    public static final Duration ENDED_VISIBLE = Duration.ofHours(24);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MeetingType type;

    @Column(nullable = false, length = 30)
    private String title;

    @Column(length = 200)
    private String description;

    /** 고민 묶음 코드(ThemeCode 이름). */
    @Column(nullable = false, length = 30)
    private String category;

    /** 예약 모임만. 실시간은 null. */
    private LocalDateTime startAt;

    @Column(nullable = false)
    private int capacity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MeetingStatus status;

    @Column(nullable = false)
    private long hostUserId;

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private CancelReason cancelReason;

    /** 오늘의 그림 주제 제목. 만들 때 서버가 고민에 맞게 정한다. */
    @Column(length = 100)
    private String topicTitle;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime endedAt;

    /** 시작한 뒤의 진행 단계. 시작 전에는 null. */
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private MeetingPhase phase;

    private LocalDateTime startedAt;

    protected Meeting() {}

    public Meeting(MeetingType type, String title, String description, String category, LocalDateTime startAt,
                   int capacity, long hostUserId, String topicTitle, LocalDateTime now) {
        this.type = type;
        this.title = title;
        this.description = description;
        this.category = category;
        this.startAt = startAt;
        this.capacity = capacity;
        this.hostUserId = hostUserId;
        this.topicTitle = topicTitle;
        this.status = type == MeetingType.LIVE ? MeetingStatus.OPEN : MeetingStatus.RECRUITING;
        this.createdAt = now;
    }

    /** 저장된 상태에 시각을 반영한 지금의 상태. 예약 모임은 시작 5분 전부터 OPEN으로 보인다. */
    public MeetingStatus statusAt(LocalDateTime now) {
        if (status == MeetingStatus.RECRUITING && type == MeetingType.SCHEDULED
                && !now.isBefore(startAt.minus(ENTER_OPEN_BEFORE))) {
            return MeetingStatus.OPEN;
        }
        return status;
    }

    /** 종료 후 24시간이 지나 상세를 더 보여 주지 않는 모임인지. */
    public boolean expired(LocalDateTime now) {
        return status == MeetingStatus.ENDED && endedAt != null && now.isAfter(endedAt.plus(ENDED_VISIBLE));
    }

    public Long getId() { return id; }
    public MeetingType getType() { return type; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public String getCategory() { return category; }
    public LocalDateTime getStartAt() { return startAt; }
    public int getCapacity() { return capacity; }
    public MeetingStatus getStatus() { return status; }
    public long getHostUserId() { return hostUserId; }
    public CancelReason getCancelReason() { return cancelReason; }
    public String getTopicTitle() { return topicTitle; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getEndedAt() { return endedAt; }

    /** 시작·종료·취소 같은 상태 변경은 이후 단위(meeting-start 등)에서 이 메서드들로 한다. */
    public void changeStatus(MeetingStatus status, LocalDateTime now) {
        this.status = status;
        if (status == MeetingStatus.ENDED || status == MeetingStatus.CANCELED) {
            this.endedAt = now;
        }
    }

    public MeetingPhase getPhase() { return phase; }
    public LocalDateTime getStartedAt() { return startedAt; }

    /** 모임을 시작한다. 진행 중(IN_PROGRESS) 상태와 첫 단계(INTRO)로 바꾼다. */
    public void start(LocalDateTime now) {
        this.status = MeetingStatus.IN_PROGRESS;
        this.phase = MeetingPhase.INTRO;
        this.startedAt = now;
    }

    /** 진행 단계를 하나 앞으로 옮긴다. 마지막 단계에서는 아무것도 하지 않고 false를 돌려준다. */
    public boolean advancePhase() {
        if (phase == null || phase.isLast()) {
            return false;
        }
        this.phase = phase.next();
        return true;
    }

    public void cancel(CancelReason reason, LocalDateTime now) {
        this.cancelReason = reason;
        changeStatus(MeetingStatus.CANCELED, now);
    }
}
