package com.ganzithon.moyang.user.dto;

import com.ganzithon.moyang.user.domain.UserOnboarding;
import java.util.List;

public final class OnboardingDtos {
    private OnboardingDtos() {}

    /** 온보딩 저장 요청. 값 검증은 OnboardingService가 순서대로 한다(형식 → 코드 → 조건부 텍스트). */
    public record OnboardingRequest(
            Integer birthYear,
            String gender,
            String artExperience,
            String themeCode,
            String themeText,
            String lifeStatus,
            String lifeStatusText,
            List<String> emotions,
            String emotionText,
            String extraStory) {}

    /** 저장된 온보딩 답. 내 정보 조회에서도 같은 모양으로 쓴다. */
    public record OnboardingData(
            Integer birthYear,
            String gender,
            String artExperience,
            String themeCode,
            String themeText,
            String lifeStatus,
            String lifeStatusText,
            List<String> emotions,
            String emotionText,
            String extraStory) {

        public static OnboardingData from(UserOnboarding o) {
            return new OnboardingData(o.getBirthYear(), o.getGender(), o.getArtExperience(), o.getThemeCode(),
                    o.getThemeText(), o.getLifeStatus(), o.getLifeStatusText(), o.getEmotions(),
                    o.getEmotionText(), o.getExtraStory());
        }
    }

    public record OnboardingResponse(boolean onboardingCompleted, OnboardingData onboarding) {}
}
