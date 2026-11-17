package com.ganzithon.moyang.meeting.service;

import com.ganzithon.moyang.meeting.domain.Meeting;
import com.ganzithon.moyang.topic.domain.ThemeCode;
import java.util.List;

/** 사용자가 정하지 않고 서버가 채우는 모임 기본 문구. */
final class MeetingDefaults {
    private MeetingDefaults() {}

    /** 방장 포함 2명 이상이면 시작할 수 있다. */
    static final int MIN_PARTICIPANTS = 2;

    static final List<String> STEPS = List.of("가볍게 자기소개하기", "오늘의 주제로 그림 그리기",
            "그림으로 이야기 나누기", "오늘의 이야기 마무리하기");

    static final List<String> RECOMMENDED_FOR = List.of(
            "비슷한 고민을 가진 사람들과 그림으로 이야기를 나누고 싶은 분",
            "감정을 정리하고 싶은 분");

    /** 목록 카드에 보이는 한 줄 소개. 제목 아래에 모임 설명이 있으면 그것을, 없으면 기본 문구. */
    static String summaryOf(Meeting m) {
        if (m.getDescription() != null && !m.getDescription().isBlank()) {
            return m.getDescription();
        }
        return labelOf(m) + " 고민을 각자의 그림과 함께 나눠요.";
    }

    static List<String> tagsOf(Meeting m) {
        return List.of(labelOf(m));
    }

    private static String labelOf(Meeting m) {
        return ThemeCode.of(m.getCategory()).map(ThemeCode::label).orElse(m.getCategory());
    }
}
