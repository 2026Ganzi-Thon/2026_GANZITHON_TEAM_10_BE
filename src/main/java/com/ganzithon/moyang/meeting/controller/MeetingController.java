package com.ganzithon.moyang.meeting.controller;

import com.ganzithon.moyang.common.port.Ports.CurrentUserProvider;
import com.ganzithon.moyang.common.response.ApiResponse;
import com.ganzithon.moyang.meeting.dto.MeetingDtos.CreateMeetingRequest;
import com.ganzithon.moyang.meeting.dto.MeetingDtos.CreateMeetingResponse;
import com.ganzithon.moyang.meeting.dto.MeetingDtos.JoinMeetingRequest;
import com.ganzithon.moyang.meeting.dto.MeetingDtos.JoinMeetingResponse;
import com.ganzithon.moyang.meeting.dto.MeetingDtos.MeetingDetailResponse;
import com.ganzithon.moyang.meeting.dto.MeetingDtos.MeetingListResponse;
import com.ganzithon.moyang.meeting.dto.MeetingDtos.MyMeetingsResponse;
import com.ganzithon.moyang.meeting.service.MeetingCommandService;
import com.ganzithon.moyang.meeting.service.MeetingQueryService;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/meetings")
public class MeetingController {

    private final MeetingQueryService query;
    private final MeetingCommandService command;
    private final CurrentUserProvider currentUser;

    public MeetingController(MeetingQueryService query, MeetingCommandService command,
                             CurrentUserProvider currentUser) {
        this.query = query;
        this.command = command;
        this.currentUser = currentUser;
    }

    @Operation(summary = "모임 만들기",
            description = "type=SCHEDULED(미리 약속한 모양)는 startAt 필수(지금 이후 ~ 최대 7일 뒤), LIVE(지금 모이는 모양)는 startAt 없이 "
                    + "바로 대기실(OPEN). 정원은 2·4·6. 방장 양털 -1(생성 실패 시 같이 되돌림)이고 방장도 참여자로 들어가 participantCount는 1. "
                    + "오류: INVALID_CAPACITY, START_AT_REQUIRED, INVALID_OPTION_CODE, INVALID_NICKNAME, NOT_ENOUGH_WOOL(양털 부족, 409).")
    @PostMapping
    public ApiResponse<CreateMeetingResponse> create(@RequestBody CreateMeetingRequest request) {
        return ApiResponse.ok(command.create(currentUser.currentUserId(), request));
    }

    @Operation(summary = "모임 신청 · 대기실 입장",
            description = "예약 모임은 신청, 실시간 모임은 대기실 입장. 이 API를 부르는 순간 양털 -1(저장과 한 트랜잭션, 양털이 모자라면 같이 되돌림). "
                    + "meetingNickname(2~15자)은 생략하면 내 양 닉네임. 시작 후·종료·취소된 모임은 신규 입장 불가. "
                    + "오류: MEETING_NOT_FOUND(404), MEETING_FULL·ALREADY_JOINED·MEETING_NOT_JOINABLE·NOT_ENOUGH_WOOL(409), INVALID_NICKNAME(400).")
    @PostMapping("/{meetingId}/participants")
    public ApiResponse<JoinMeetingResponse> join(@PathVariable long meetingId,
                                                 @RequestBody(required = false) JoinMeetingRequest request) {
        return ApiResponse.ok(command.join(currentUser.currentUserId(), meetingId,
                request == null ? null : request.meetingNickname()));
    }

    @Operation(summary = "모임 목록 조회",
            description = "type=SCHEDULED(미리 약속한 모양) 또는 LIVE(지금 모이는 모양). category(고민 묶음 코드)는 선택. "
                    + "시작 전 모임(RECRUITING·OPEN)만 나오고, 진행 중·종료·취소 모임은 빠진다. "
                    + "recommendedMeetingId는 내 고민과 같은 카테고리 중 가장 빨리 시작하는 예약 모임(없으면 null). "
                    + "프론트는 status 값만 보고 화면을 정한다.")
    @GetMapping
    public ApiResponse<MeetingListResponse> list(@RequestParam(required = false) String type,
                                                 @RequestParam(required = false) String category) {
        return ApiResponse.ok(query.list(currentUser.currentUserId(), type, category));
    }

    @Operation(summary = "내가 참여 중인 모임",
            description = "신청했거나 방장인, 아직 끝나지 않은 모임(RECRUITING·OPEN·IN_PROGRESS)을 시작 시각 순으로. "
                    + "모임 수는 meetings 개수, 가장 가까운 일정은 startAt으로 프론트가 계산한다(실시간은 startAt이 null).")
    @GetMapping("/me")
    public ApiResponse<MyMeetingsResponse> mine() {
        return ApiResponse.ok(query.mine(currentUser.currentUserId()));
    }

    @Operation(summary = "모임 상세 조회 · 대기실",
            description = "status, isHost, canStart(실시간·방장·2명 이상), canSkip(주제 건너뛰기 권한), "
                    + "enterOpenAt(예정 5분 전), cancelRefundUntil(예정 10분 전), cancelReason을 내려준다. "
                    + "프론트는 시간 계산 없이 이 값으로 버튼과 화면을 정한다. 예약 모임의 topic은 시작 전까지 null.")
    @GetMapping("/{meetingId}")
    public ApiResponse<MeetingDetailResponse> detail(@PathVariable long meetingId) {
        return ApiResponse.ok(query.detail(currentUser.currentUserId(), meetingId));
    }
}
