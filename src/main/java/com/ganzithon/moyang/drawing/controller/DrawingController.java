package com.ganzithon.moyang.drawing.controller;

import com.ganzithon.moyang.common.response.ApiResponse;
import com.ganzithon.moyang.ai.domain.Conversation;
import com.ganzithon.moyang.ai.dto.ConversationDtos;
import com.ganzithon.moyang.ai.repository.ConversationRepository;
import com.ganzithon.moyang.common.port.Ports.CurrentUserProvider;
import com.ganzithon.moyang.common.port.Ports.TopicReader;
import com.ganzithon.moyang.drawing.domain.Drawing;
import com.ganzithon.moyang.drawing.dto.DrawingDtos.DrawingInfoResponse;
import com.ganzithon.moyang.drawing.dto.DrawingDtos.DrawingUploadResponse;
import com.ganzithon.moyang.drawing.dto.DrawingDtos.TopicView;
import com.ganzithon.moyang.drawing.service.DrawingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Duration;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "그림", description = "그림 올리기 · 조회. imageUrl은 상대경로이고 이미지 요청에도 인증 토큰이 필요하다")
@RestController
@RequestMapping("/api/drawings")
public class DrawingController {

    private final DrawingService service;
    private final CurrentUserProvider currentUser;
    private final TopicReader topics;
    private final ConversationRepository conversations;

    public DrawingController(DrawingService service, CurrentUserProvider currentUser, TopicReader topics,
                             ConversationRepository conversations) {
        this.service = service;
        this.currentUser = currentUser;
        this.topics = topics;
        this.conversations = conversations;
    }

    @Operation(summary = "그림 올리기", description = "multipart: image(jpg/png, 10MB), inputType(PHOTO · UPLOAD · CANVAS), "
            + "topicId(선택, 자유 주제 고정 ID), note(선택, 200자). 응답의 recordId가 이후 대화 · 회고 저장 · 기록의 식별자다.")
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<DrawingUploadResponse> upload(@RequestPart(value = "image", required = false) MultipartFile image,
                                              @RequestParam(required = false) String inputType,
                                              @RequestParam(required = false) Long topicId,
                                              @RequestParam(required = false) String note) {
        Drawing d = service.upload(currentUser.currentUserId(), image, inputType, topicId, note);
        return ApiResponse.ok(new DrawingUploadResponse(d.getId(), d.getRecordId(), d.imageUrl(), d.getInputType().name(),
                d.getTopicId(), d.getNote(), ConversationDtos.kst(d.getCreatedAt())));
    }

    @Operation(summary = "그림 정보", description = "본인 그림만. 대화를 시작했으면 conversationId가 들어간다.")
    @GetMapping("/{drawingId}")
    public ApiResponse<DrawingInfoResponse> info(@PathVariable long drawingId) {
        Drawing d = service.getOwned(currentUser.currentUserId(), drawingId);
        TopicView topic = d.getTopicId() == null ? null
                : topics.find(d.getTopicId()).map(t -> new TopicView(t.topicId(), t.title())).orElse(null);
        Long conversationId = conversations.findByRecordId(d.getRecordId()).map(Conversation::getId).orElse(null);
        return ApiResponse.ok(new DrawingInfoResponse(d.getId(), d.getRecordId(), d.imageUrl(), d.getInputType().name(),
                topic, d.getNote(), conversationId, ConversationDtos.kst(d.getCreatedAt())));
    }

    @Operation(summary = "그림 이미지", description = "이미지 바이너리. 그림 주인, 또는 이 그림이 공유된 모임의 참여자만 볼 수 있다. "
            + "img 태그로는 토큰을 못 실으니 fetch(Authorization 헤더) → blob URL로 보여준다.")
    @GetMapping("/{drawingId}/image")
    public ResponseEntity<Resource> image(@PathVariable long drawingId) {
        Drawing d = service.getViewable(currentUser.currentUserId(), drawingId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(d.getContentType()))
                .cacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePrivate())
                .header("X-Content-Type-Options", "nosniff")
                .body(service.loadImage(d));
    }
}
