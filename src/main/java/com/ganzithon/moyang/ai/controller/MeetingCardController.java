package com.ganzithon.moyang.ai.controller;

import com.ganzithon.moyang.ai.service.MeetingCardService;

import com.ganzithon.moyang.common.response.ApiResponse;
import com.ganzithon.moyang.ai.dto.ConversationDtos.MeetingCardResponse;
import com.ganzithon.moyang.common.port.Ports.CurrentUserProvider;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "AI 그림 대화")
@RestController
public class MeetingCardController {
    private final MeetingCardService service;
    private final CurrentUserProvider currentUser;

    public MeetingCardController(MeetingCardService service, CurrentUserProvider currentUser) {
        this.service = service;
        this.currentUser = currentUser;
    }

    @Operation(summary = "모임 회고 카드", description = "모임이 끝난 뒤 본인 발언과 본인 그림만으로 만든 카드. "
            + "참여자가 아니면 403 NOT_PARTICIPANT, 진행 중이면 409 MEETING_NOT_ENDED, 그림을 안 냈으면 404 NO_DRAWING_IN_MEETING.")
    @GetMapping("/api/meetings/{meetingId}/reflection")
    public ApiResponse<MeetingCardResponse> reflection(@PathVariable long meetingId) {
        return ApiResponse.ok(service.get(currentUser.currentUserId(), meetingId));
    }
}
