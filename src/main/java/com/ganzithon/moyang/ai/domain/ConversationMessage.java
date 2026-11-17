package com.ganzithon.moyang.ai.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "conversation_messages", indexes = @Index(columnList = "conversationId"))
public class ConversationMessage {

    public enum Role { AI, USER }

    public enum Type { QUESTION, ANSWER, DONT_KNOW, SKIP }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long conversationId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Role role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private Type type;

    /** 모르겠어요 · 건너뛸게요면 null. */
    @Column(length = 1000)
    private String content;

    @Column(nullable = false)
    private int questionOrder;

    /** AI 실패로 나간 기본 질문. */
    @Column(nullable = false)
    private boolean isFallback;

    /** 위기 표현 감지. 민감정보이므로 로그에 남기지 않는다. */
    @Column(nullable = false)
    private boolean needsSupport;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    protected ConversationMessage() {}

    public static ConversationMessage question(Long conversationId, String content, int order, boolean fallback) {
        ConversationMessage m = new ConversationMessage();
        m.conversationId = conversationId;
        m.role = Role.AI;
        m.type = Type.QUESTION;
        m.content = content;
        m.questionOrder = order;
        m.isFallback = fallback;
        return m;
    }

    public static ConversationMessage user(Long conversationId, Type type, String content, int order,
                                           boolean needsSupport) {
        ConversationMessage m = new ConversationMessage();
        m.conversationId = conversationId;
        m.role = Role.USER;
        m.type = type;
        m.content = content;
        m.questionOrder = order;
        m.needsSupport = needsSupport;
        return m;
    }

    public void markNeedsSupport() {
        this.needsSupport = true;
    }

    public boolean isAi() { return role == Role.AI; }

    public Long getId() { return id; }
    public Role getRole() { return role; }
    public Type getType() { return type; }
    public String getContent() { return content; }
    public int getQuestionOrder() { return questionOrder; }
    public boolean isNeedsSupport() { return needsSupport; }
    public Instant getCreatedAt() { return createdAt; }
}
