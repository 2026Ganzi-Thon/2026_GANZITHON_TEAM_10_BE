package com.ganzithon.moyang.user.controller;

import com.ganzithon.moyang.common.port.Ports.CurrentUserProvider;
import com.ganzithon.moyang.common.response.ApiResponse;
import com.ganzithon.moyang.user.dto.OnboardingDtos.OnboardingRequest;
import com.ganzithon.moyang.user.dto.OnboardingDtos.OnboardingResponse;
import com.ganzithon.moyang.user.dto.UserDtos.MeResponse;
import com.ganzithon.moyang.user.service.OnboardingService;
import com.ganzithon.moyang.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users/me")
public class UserController {

    private final UserService userService;
    private final OnboardingService onboarding;
    private final CurrentUserProvider currentUser;

    public UserController(UserService userService, OnboardingService onboarding, CurrentUserProvider currentUser) {
        this.userService = userService;
        this.onboarding = onboarding;
        this.currentUser = currentUser;
    }

    @Operation(summary = "내 정보 조회",
            description = "닉네임 · 온보딩 완료 여부 · 양 색상 · 온보딩 답을 돌려줍니다. 온보딩 전이면 onboarding이 null이고, "
                    + "건너뛴 항목과 고르기 전인 sheepColor도 null입니다. 토큰의 사용자가 없으면 404 USER_NOT_FOUND입니다.")
    @GetMapping
    public ApiResponse<MeResponse> me() {
        return ApiResponse.ok(userService.me(currentUser.currentUserId()));
    }

    @Operation(summary = "온보딩 답 저장",
            description = "보낸 값으로 통째로 덮어씁니다(첫 저장과 '상담 시작하기' 다시 하기 모두). themeCode만 필수이고 나머지는 생략하면 null입니다. "
                    + "themeCode가 DIRECT면 themeText, lifeStatus가 ETC면 lifeStatusText, emotions에 DIRECT가 있으면 emotionText가 필요합니다(각 100자 이하). "
                    + "emotions는 최대 2개·중복 불가, extraStory는 500자 이하입니다. "
                    + "400 INVALID_REQUEST(필수·형식·글자 수·개수)가 먼저, 없는 코드는 400 INVALID_OPTION_CODE입니다. "
                    + "저장하면 추천 주제(GET /api/topics/recommended)가 내 고민 기준으로 동작합니다.")
    @PutMapping("/onboarding")
    public ApiResponse<OnboardingResponse> saveOnboarding(@RequestBody OnboardingRequest request) {
        return ApiResponse.ok(onboarding.save(currentUser.currentUserId(), request));
    }
}
