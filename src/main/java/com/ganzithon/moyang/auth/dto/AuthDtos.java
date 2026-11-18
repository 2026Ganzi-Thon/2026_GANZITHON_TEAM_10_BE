package com.ganzithon.moyang.auth.dto;

import jakarta.validation.constraints.NotBlank;

public final class AuthDtos {
    private AuthDtos() {}

    public record LoginRequest(@NotBlank String loginId, @NotBlank String password) {}

    public record UserSummary(long userId, String nickname, boolean onboardingCompleted) {}

    public record LoginResponse(String accessToken, String tokenType, long expiresIn, UserSummary user) {}
}
