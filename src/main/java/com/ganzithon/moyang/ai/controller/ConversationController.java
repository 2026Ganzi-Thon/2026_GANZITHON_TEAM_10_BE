package com.ganzithon.moyang.ai.controller;

import com.ganzithon.moyang.ai.service.ConversationService;

import com.ganzithon.moyang.common.response.ApiResponse;
import com.ganzithon.moyang.ai.dto.ConversationDtos.DetailResponse;
import com.ganzithon.moyang.ai.dto.ConversationDtos.EndRequest;
import com.ganzithon.moyang.ai.dto.ConversationDtos.EndResponse;
import com.ganzithon.moyang.ai.dto.ConversationDtos.InProgressResponse;
import com.ganzithon.moyang.ai.dto.ConversationDtos.MessageRequest;
import com.ganzithon.moyang.ai.dto.ConversationDtos.ReplyResponse;
import com.ganzithon.moyang.ai.dto.ConversationDtos.StartRequest;
import com.ganzithon.moyang.ai.dto.ConversationDtos.StartResponse;
import com.ganzithon.moyang.common.port.Ports.CurrentUserProvider;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "AI 그림 대화", description = "그림을 올린 뒤 AI 질문에 답하고, 마치면 회고 카드가 만들어진다")
@RestController
@RequestMapping("/api/conversations")
public class ConversationController {
    private final ConversationService service;
    private final CurrentUserProvider currentUser;

    public ConversationController(ConversationService service, CurrentUserProvider currentUser) {
        this.service = service;
        this.currentUser = currentUser;
    }

    @Operation(summary = "대화 시작", description = "본인 그림으로 대화를 시작하고 첫 질문을 받는다. 그림당 1번 (중복이면 409 CONVERSATION_ALREADY_EXISTS). "
            + "기본 질문은 freeQuestions(5)개. AI가 실패하면 fallback=true로 기본 질문이 나간다.")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<StartResponse> start(@RequestBody StartRequest request) {
        return ApiResponse.ok(service.start(currentUser.currentUserId(), request.drawingId()));
    }

    @Operation(summary = "답하고 다음 질문 받기", description = "type: ANSWER(content 필수, 1000자) · DONT_KNOW · SKIP. "
            + "기본 질문을 다 쓰면 question=null, isLastQuestion=true, canExtend로 연장 여부를 알려준다. "
            + "연장은 {\"useWool\":true}만 보내거나 답과 함께 보낸다 (양털 1개, 최대 10번째 질문까지). "
            + "위기 표현이면 question=null, supportNotice(109 상담 안내)가 오고 양털은 차감하지 않는다.")
    @PostMapping("/{conversationId}/messages")
    public ApiResponse<ReplyResponse> reply(@PathVariable long conversationId, @RequestBody MessageRequest request) {
        return ApiResponse.ok(service.reply(currentUser.currentUserId(), conversationId, request));
    }

    @Operation(summary = "대화 마치기", description = "{\"status\":\"ENDED\"}. 회고 카드(whatISaid · thoughts · todaysStory)를 만든다. "
            + "응답의 recordId로 회고 저장을 한다. 위기 표현이 담긴 답은 카드 재료에서 빠진다.")
    @PutMapping("/{conversationId}")
    public ApiResponse<EndResponse> end(@PathVariable long conversationId, @RequestBody EndRequest request) {
        return ApiResponse.ok(service.end(currentUser.currentUserId(), conversationId, request.status()));
    }

    @Operation(summary = "대화 상세", description = "전체 메시지와(끝났으면) 회고 카드. 본인 대화만.")
    @GetMapping("/{conversationId}")
    public ApiResponse<DetailResponse> detail(@PathVariable long conversationId) {
        return ApiResponse.ok(service.detail(currentUser.currentUserId(), conversationId));
    }

    @Operation(summary = "진행 중인 대화 이어가기", description = "?status=IN_PROGRESS 필수. 없으면 data.conversation=null.")
    @GetMapping
    public ApiResponse<InProgressResponse> inProgress(@RequestParam(required = false) String status) {
        return ApiResponse.ok(service.inProgress(currentUser.currentUserId(), status));
    }
}
