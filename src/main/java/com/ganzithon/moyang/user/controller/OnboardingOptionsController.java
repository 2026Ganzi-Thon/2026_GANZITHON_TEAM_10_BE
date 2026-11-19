package com.ganzithon.moyang.user.controller;

import com.ganzithon.moyang.common.response.ApiResponse;
import com.ganzithon.moyang.user.dto.OnboardingOptionsResponse;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 온보딩 선택지 목록. 로그인 전에도 볼 수 있어서 토큰을 확인하지 않는다. */
@RestController
@RequestMapping("/api/onboarding")
public class OnboardingOptionsController {

    @Operation(summary = "온보딩 선택지 목록 (인증 없음)",
            description = "고민(themes) · 감정(emotions) · 생활환경(lifeStatuses) · 미술 경험(artExperiences)의 코드와 문구를 돌려줍니다. "
                    + "로그인 전에도 조회할 수 있고, 온보딩 저장 API가 받는 코드와 항상 같습니다. "
                    + "감정에서 DIRECT를 고르면 emotionText가, 생활환경에서 ETC를 고르면 lifeStatusText가, 고민에서 DIRECT를 고르면 themeText가 필요합니다.",
            security = {})
    @GetMapping("/options")
    public ApiResponse<OnboardingOptionsResponse> options() {
        return ApiResponse.ok(OnboardingOptionsResponse.current());
    }
}
