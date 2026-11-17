package com.ganzithon.moyang.topic.controller;

import com.ganzithon.moyang.common.port.Ports.CurrentUserProvider;
import com.ganzithon.moyang.common.response.ApiResponse;
import com.ganzithon.moyang.topic.dto.TopicDtos.RecommendedTopicsResponse;
import com.ganzithon.moyang.topic.service.TopicService;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/topics")
public class TopicController {

    private final TopicService service;
    private final CurrentUserProvider currentUser;

    public TopicController(TopicService service, CurrentUserProvider currentUser) {
        this.service = service;
        this.currentUser = currentUser;
    }

    @Operation(summary = "추천 그림 주제",
            description = "내 고민에 맞는 그림 주제 2~3개와 힌트 질문. themeCode를 생략하면 온보딩에서 고른 내 고민 기준이고, "
                    + "온보딩 전이면 409 ONBOARDING_NOT_COMPLETED. 없는 코드는 400 INVALID_OPTION_CODE. "
                    + "자유 주제는 목록에 없고 고정 topicId 1을 그림 업로드에 쓴다.")
    @GetMapping("/recommended")
    public ApiResponse<RecommendedTopicsResponse> recommended(@RequestParam(required = false) String themeCode) {
        return ApiResponse.ok(service.recommended(currentUser.currentUserId(), themeCode));
    }
}
