package com.ganzithon.moyang.user.dto;

import com.ganzithon.moyang.user.domain.OnboardingOptions;
import com.ganzithon.moyang.user.domain.OnboardingOptions.Option;
import java.util.List;

/** 온보딩 선택지 목록 응답. 코드표는 OnboardingOptions 한 곳에서만 관리한다(저장 API 검증과 같은 값). */
public record OnboardingOptionsResponse(
        List<Option> themes,
        List<Option> emotions,
        List<Option> lifeStatuses,
        List<Option> artExperiences) {

    public static OnboardingOptionsResponse current() {
        return new OnboardingOptionsResponse(OnboardingOptions.THEMES, OnboardingOptions.EMOTIONS,
                OnboardingOptions.LIFE_STATUSES, OnboardingOptions.ART_EXPERIENCES);
    }
}
