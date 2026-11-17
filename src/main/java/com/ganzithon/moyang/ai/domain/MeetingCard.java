package com.ganzithon.moyang.ai.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;

/** 모임 종료 때 참여자별로 만든 회고 카드. 모임 채팅이 삭제돼도 남는다. */
@Entity
@Table(name = "meeting_cards", uniqueConstraints = @UniqueConstraint(columnNames = {"meetingId", "userId"}))
public class MeetingCard {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long meetingId;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private Long recordId;

    @Column(nullable = false)
    private Long drawingId;

    @Column(columnDefinition = "TEXT")
    private String whatISaid;

    @Column(columnDefinition = "TEXT")
    private String thoughts;

    @Column(columnDefinition = "TEXT")
    private String todaysStory;

    @Column(nullable = false)
    private boolean fallback;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    protected MeetingCard() {}

    public MeetingCard(Long meetingId, Long userId, Long recordId, Long drawingId, String whatISaid,
                       String thoughts, String todaysStory, boolean fallback) {
        this.meetingId = meetingId;
        this.userId = userId;
        this.recordId = recordId;
        this.drawingId = drawingId;
        this.whatISaid = whatISaid;
        this.thoughts = thoughts;
        this.todaysStory = todaysStory;
        this.fallback = fallback;
    }

    public Long getRecordId() { return recordId; }
    public Long getDrawingId() { return drawingId; }
    public String getWhatISaid() { return whatISaid; }
    public String getThoughts() { return thoughts; }
    public String getTodaysStory() { return todaysStory; }
    public boolean isFallback() { return fallback; }
}
