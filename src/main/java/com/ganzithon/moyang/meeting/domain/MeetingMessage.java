package com.ganzithon.moyang.meeting.domain;

import com.ganzithon.moyang.meeting.domain.MeetingEnums.MessageType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/**
 * 모임 채팅 메시지. 폴링(afterId)이 id 순서에 기대므로, 저장은 항상 모임 행 락 안에서 해서 id 순서와 커밋 순서가 같게 한다.
 * SYSTEM 메시지는 userId가 없다. 시각은 한국 시간(KST) LocalDateTime으로 저장하고 응답에서 +09:00을 붙인다.
 */
@Entity
@Table(name = "meeting_messages", indexes = @Index(name = "idx_message_meeting", columnList = "meetingId, id"))
public class MeetingMessage {

    public static final int MAX_CONTENT = 500;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private long meetingId;

    /** 보낸 사람. SYSTEM 메시지는 null. */
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MessageType type;

    // 글자 수 검사(500자)는 서비스에서 코드 포인트 기준으로 한다. 컬럼은 이모지(UTF-16 2칸)를 세는 DB(H2)에서도 넘치지 않게 2배로 잡는다.
    @Column(length = MAX_CONTENT * 2)
    private String content;

    /** DRAWING 메시지가 가리키는 그림. 그림 파일은 drawing 도메인이 가진다. */
    private Long drawingId;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected MeetingMessage() {}

    private MeetingMessage(long meetingId, Long userId, MessageType type, String content, Long drawingId,
                           LocalDateTime createdAt) {
        this.meetingId = meetingId;
        this.userId = userId;
        this.type = type;
        this.content = content;
        this.drawingId = drawingId;
        this.createdAt = createdAt;
    }

    public static MeetingMessage text(long meetingId, long userId, String content, LocalDateTime now) {
        return new MeetingMessage(meetingId, userId, MessageType.TEXT, content, null, now);
    }

    public static MeetingMessage drawing(long meetingId, long userId, long drawingId, String content,
                                         LocalDateTime now) {
        return new MeetingMessage(meetingId, userId, MessageType.DRAWING, content, drawingId, now);
    }

    public static MeetingMessage system(long meetingId, String content, LocalDateTime now) {
        return new MeetingMessage(meetingId, null, MessageType.SYSTEM, content, null, now);
    }

    public Long getId() { return id; }
    public long getMeetingId() { return meetingId; }
    public Long getUserId() { return userId; }
    public MessageType getType() { return type; }
    public String getContent() { return content; }
    public Long getDrawingId() { return drawingId; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
