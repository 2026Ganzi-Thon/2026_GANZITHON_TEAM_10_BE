package com.ganzithon.moyang.user.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

/**
 * 사용자의 온보딩 답. 사용자마다 한 줄(userId가 PK)이고, 다시 저장하면 통째로 덮어쓴다.
 * 다른 도메인처럼 users 테이블과 FK는 두지 않고 userId 숫자만 가진다.
 */
@Entity
@Table(name = "user_onboardings")
public class UserOnboarding {

    private static final String SEPARATOR = ",";

    @Id
    private Long userId;

    private Integer birthYear;

    @Column(length = 20)
    private String gender;

    @Column(length = 20)
    private String artExperience;

    @Column(nullable = false, length = 30)
    private String themeCode;

    @Column(length = 100)
    private String themeText;

    @Column(length = 20)
    private String lifeStatus;

    @Column(length = 100)
    private String lifeStatusText;

    /** 감정 코드를 쉼표로 이은 문자열(최대 2개). 비어 있으면 null. */
    @Column(length = 100)
    private String emotions;

    @Column(length = 100)
    private String emotionText;

    @Column(length = 500)
    private String extraStory;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    protected UserOnboarding() {}

    public UserOnboarding(Long userId) {
        this.userId = userId;
    }

    /** 보낸 값으로 모든 칸을 덮어쓴다. 값 검증은 서비스에서 끝낸 뒤 부른다. */
    public void overwrite(Integer birthYear, String gender, String artExperience, String themeCode,
                          String themeText, String lifeStatus, String lifeStatusText,
                          List<String> emotions, String emotionText, String extraStory) {
        this.birthYear = birthYear;
        this.gender = gender;
        this.artExperience = artExperience;
        this.themeCode = themeCode;
        this.themeText = themeText;
        this.lifeStatus = lifeStatus;
        this.lifeStatusText = lifeStatusText;
        this.emotions = emotions.isEmpty() ? null : String.join(SEPARATOR, emotions);
        this.emotionText = emotionText;
        this.extraStory = extraStory;
        this.updatedAt = LocalDateTime.now();
    }

    public Long getUserId() { return userId; }
    public Integer getBirthYear() { return birthYear; }
    public String getGender() { return gender; }
    public String getArtExperience() { return artExperience; }
    public String getThemeCode() { return themeCode; }
    public String getThemeText() { return themeText; }
    public String getLifeStatus() { return lifeStatus; }
    public String getLifeStatusText() { return lifeStatusText; }
    public String getEmotionText() { return emotionText; }
    public String getExtraStory() { return extraStory; }

    public List<String> getEmotions() {
        return emotions == null || emotions.isBlank() ? List.of() : Arrays.asList(emotions.split(SEPARATOR));
    }
}
