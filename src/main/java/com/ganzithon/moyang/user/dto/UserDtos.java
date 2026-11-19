package com.ganzithon.moyang.user.dto;

import com.ganzithon.moyang.user.dto.OnboardingDtos.OnboardingData;

public final class UserDtos {
    private UserDtos() {}

    /** 내 정보. 온보딩 전이면 onboarding이 null, 양 색상을 고르기 전이면 sheepColor가 null. */
    public record MeResponse(long userId, String nickname, boolean onboardingCompleted,
                             String sheepColor, OnboardingData onboarding) {}

    /** 닉네임 수정 요청. 앞뒤 공백을 지운 뒤 2~15자인지 서버가 검사한다. */
    public record NicknameRequest(String nickname) {}

    /** 저장된(공백을 지운) 닉네임. */
    public record NicknameResponse(long userId, String nickname) {}

    /** 양 색상 수정 요청. 코드값은 프론트가 정하고, 서버는 형식만 검사한다. */
    public record SheepColorRequest(String sheepColor) {}

    public record SheepColorResponse(long userId, String sheepColor) {}
}
