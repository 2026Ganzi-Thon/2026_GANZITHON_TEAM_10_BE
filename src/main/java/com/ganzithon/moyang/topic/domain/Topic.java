package com.ganzithon.moyang.topic.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.Arrays;
import java.util.List;

/** 그림 주제. 데이터는 TopicSeeder가 시작할 때 넣는다. id는 자동 번호가 아니라 고정값이다. */
@Entity
@Table(name = "topics")
public class Topic {

    /** 자유 주제용 고정 ID. 추천 목록에는 나오지 않고, 그림 업로드 때 topicId로만 쓴다. */
    public static final long FREE_TOPIC_ID = 1L;

    private static final String HINT_SEPARATOR = "\n";

    @Id
    private Long id;

    /** 고민 묶음(ThemeCode 이름). 자유 주제는 null. */
    @Column(length = 30)
    private String themeCode;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(nullable = false, length = 300)
    private String description;

    /** 힌트 질문들을 줄바꿈으로 이어 붙여 저장. 없으면 빈 문자열. */
    @Column(nullable = false, length = 1000)
    private String hintsText;

    protected Topic() {}

    public Topic(long id, ThemeCode themeCode, String title, String description, List<String> hints) {
        this.id = id;
        change(themeCode, title, description, hints);
    }

    public void change(ThemeCode themeCode, String title, String description, List<String> hints) {
        this.themeCode = themeCode == null ? null : themeCode.name();
        this.title = title;
        this.description = description;
        this.hintsText = String.join(HINT_SEPARATOR, hints);
    }

    public Long getId() { return id; }
    public String getThemeCode() { return themeCode; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }

    public List<String> getHints() {
        return hintsText.isEmpty() ? List.of() : Arrays.asList(hintsText.split(HINT_SEPARATOR));
    }
}
