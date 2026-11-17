package com.ganzithon.moyang.drawing.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "drawings")
public class Drawing {

    public enum InputType { PHOTO, UPLOAD, CANVAS }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private Long recordId;

    @Column(nullable = false)
    private Long userId;

    private Long topicId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private InputType inputType;

    @Column(length = 200)
    private String note;

    /** UUID 파일명. 원본 파일명은 저장하지 않는다. */
    @Column(nullable = false, unique = true, length = 100)
    private String storedFileName;

    @Column(nullable = false, length = 20)
    private String contentType;

    @Column(nullable = false)
    private int fileSize;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    protected Drawing() {}

    public Drawing(Long recordId, Long userId, Long topicId, InputType inputType, String note,
                   String storedFileName, String contentType, int fileSize) {
        this.recordId = recordId;
        this.userId = userId;
        this.topicId = topicId;
        this.inputType = inputType;
        this.note = note;
        this.storedFileName = storedFileName;
        this.contentType = contentType;
        this.fileSize = fileSize;
    }

    public String imageUrl() {
        return "/api/drawings/" + id + "/image";
    }

    public Long getId() { return id; }
    public Long getRecordId() { return recordId; }
    public Long getUserId() { return userId; }
    public Long getTopicId() { return topicId; }
    public InputType getInputType() { return inputType; }
    public String getNote() { return note; }
    public String getStoredFileName() { return storedFileName; }
    public String getContentType() { return contentType; }
    public Instant getCreatedAt() { return createdAt; }
}
