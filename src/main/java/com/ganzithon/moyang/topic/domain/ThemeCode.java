package com.ganzithon.moyang.topic.domain;

import java.util.Optional;

/** 온보딩 '고민 묶음' 코드. 노션 「온보딩 선택지 목록」의 themes와 같은 값이다. */
public enum ThemeCode {
    STUDY_CAREER, LOVE, MONEY, DAILY_FUTURE, SELF_HEALTH, RELATION_FAMILY, UNSURE, DIRECT;

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
