package com.ganzithon.moyang.user.dto;

import com.ganzithon.moyang.user.dto.OnboardingDtos.OnboardingData;

public final class UserDtos {
    private UserDtos() {}

    /** 내 정보. 온보딩 전이면 onboarding이 null, 양 색상을 고르기 전이면 sheepColor가 null. */
    public record MeResponse(long userId, String nickname, boolean onboardingCompleted,
                             String sheepColor, OnboardingData onboarding) {}
}
