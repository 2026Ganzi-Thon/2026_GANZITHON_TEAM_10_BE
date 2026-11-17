package com.ganzithon.moyang.topic.domain;

import java.util.Optional;

/** 온보딩 '고민 묶음' 코드. 노션 「온보딩 선택지 목록」의 themes와 같은 값이다. */
public enum ThemeCode {
    STUDY_CAREER("학업·진로·일"), LOVE("연애·이별"), MONEY("경제·금전"), DAILY_FUTURE("일상·미래"),
    SELF_HEALTH("나 자신·건강"), RELATION_FAMILY("인간관계·가족"), UNSURE("잘 모르겠어요"), DIRECT("기타 (직접 입력)");

    private final String label;

    ThemeCode(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public static Optional<ThemeCode> of(String code) {
        if (code == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(valueOf(code));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    /** 주제 목록을 고를 때 쓰는 묶음. '직접 말할래요'는 '잘 모르겠어요'와 같은 공통 주제를 쓴다. */
    public ThemeCode topicGroup() {
        return this == DIRECT ? UNSURE : this;
    }
}
