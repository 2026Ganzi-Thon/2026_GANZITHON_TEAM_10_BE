package com.ganzithon.moyang.ai.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;

/** AI 대화 + 회고 카드. 활동당 1개. */
@Entity
@Table(name = "conversations")
public class Conversation {

    public enum Status { IN_PROGRESS, ENDED }

    /** 양털 없이 받는 질문 수. */
    public static final int FREE_QUESTIONS = 5;
    /** 양털로 더 받을 수 있는 질문 수 (총 10개). */
    public static final int MAX_EXTRA_QUESTIONS = 5;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private Long recordId;

    @Column(nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private Status status = Status.IN_PROGRESS;

    @Column(nullable = false)
    private int questionCount;

    @Column(nullable = false)
    private int maxQuestions = FREE_QUESTIONS;

    /** 양털로 추가한 질문 수. */
    @Column(nullable = false)
    private int woolUsed;

    @Column(columnDefinition = "TEXT")
    private String cardWhatISaid;

    @Column(columnDefinition = "TEXT")
    private String cardThoughts;

    @Column(columnDefinition = "TEXT")
    private String cardTodaysStory;

    @Column(nullable = false)
    private boolean cardFallback;

    /** 그림 자세히 살펴보기 4칸 (AI가 대화 마치기 때 카드와 함께 만듦). */
    @Column(columnDefinition = "TEXT")
    private String mainElements;

    @Column(columnDefinition = "TEXT")
    private String spatialComposition;

    @Column(columnDefinition = "TEXT")
    private String lineExpression;

    @Column(columnDefinition = "TEXT")
    private String emotionMeaning;

    private Instant endedAt;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    @Column(nullable = false)
    private Instant updatedAt = Instant.now();

    /** 같은 대화에 요청이 동시에 두 번 들어왔을 때 질문이 두 번 나가지 않게 한다. */
    @Version
    private long version;

    protected Conversation() {}

    public Conversation(Long recordId, Long userId) {
        this.recordId = recordId;
        this.userId = userId;
    }

    public void asked(boolean paidWithWool) {
        questionCount++;
        if (paidWithWool) {
            woolUsed++;
        }
        updatedAt = Instant.now();
    }

    /** 대화가 바뀌지 않는 저장(답만 저장)에서도 @Version을 올려, 동시에 겹친 다른 저장 하나를 409로 막는다. */
    public void touch() {
        updatedAt = Instant.now();
    }

    public void analyzed(String mainElements, String spatialComposition, String lineExpression,
                         String emotionMeaning) {
        this.mainElements = mainElements;
        this.spatialComposition = spatialComposition;
        this.lineExpression = lineExpression;
        this.emotionMeaning = emotionMeaning;
    }

    public void end(String whatISaid, String thoughts, String todaysStory, boolean fallback) {
        status = Status.ENDED;
        cardWhatISaid = whatISaid;
        cardThoughts = thoughts;
        cardTodaysStory = todaysStory;
        cardFallback = fallback;
        endedAt = Instant.now();
        updatedAt = endedAt;
    }

    public boolean isEnded() { return status == Status.ENDED; }
    public boolean hasFreeQuestionLeft() { return questionCount < maxQuestions; }
    public boolean canAddExtraQuestion() { return questionCount < maxQuestions + MAX_EXTRA_QUESTIONS; }
    public int remainingFreeQuestions() { return Math.max(0, maxQuestions - questionCount); }

    public Long getId() { return id; }
    public Long getRecordId() { return recordId; }
    public Long getUserId() { return userId; }
    public Status getStatus() { return status; }
    public int getQuestionCount() { return questionCount; }
    public int getMaxQuestions() { return maxQuestions; }
    public long getVersion() { return version; }
    public String getCardWhatISaid() { return cardWhatISaid; }
    public String getCardThoughts() { return cardThoughts; }
    public String getCardTodaysStory() { return cardTodaysStory; }
    public boolean isCardFallback() { return cardFallback; }
    public String getMainElements() { return mainElements; }
    public String getSpatialComposition() { return spatialComposition; }
    public String getLineExpression() { return lineExpression; }
    public String getEmotionMeaning() { return emotionMeaning; }
    public Instant getCreatedAt() { return createdAt; }
}
