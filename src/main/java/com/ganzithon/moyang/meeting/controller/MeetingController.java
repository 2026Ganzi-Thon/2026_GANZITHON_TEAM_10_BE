package com.ganzithon.moyang.meeting.controller;

import com.ganzithon.moyang.common.port.Ports.CurrentUserProvider;
import com.ganzithon.moyang.common.response.ApiResponse;
import com.ganzithon.moyang.meeting.dto.MeetingDtos.CancelMeetingResponse;
import com.ganzithon.moyang.meeting.dto.MeetingDtos.LeaveMeetingResponse;
import com.ganzithon.moyang.meeting.dto.MeetingDtos.CreateMeetingRequest;
import com.ganzithon.moyang.meeting.dto.MeetingDtos.CreateMeetingResponse;
import com.ganzithon.moyang.meeting.dto.MeetingDtos.JoinMeetingRequest;
import com.ganzithon.moyang.meeting.dto.MeetingDtos.JoinMeetingResponse;
import com.ganzithon.moyang.meeting.dto.MeetingDtos.MeetingDetailResponse;
import com.ganzithon.moyang.meeting.dto.MeetingDtos.MeetingListResponse;
import com.ganzithon.moyang.meeting.dto.MeetingDtos.MyMeetingsResponse;
import com.ganzithon.moyang.meeting.dto.MeetingDtos.EnterMeetingResponse;
import com.ganzithon.moyang.meeting.dto.MeetingDtos.StartMeetingResponse;
import com.ganzithon.moyang.meeting.service.MeetingCommandService;
import com.ganzithon.moyang.meeting.service.MeetingLifecycleService;
import com.ganzithon.moyang.meeting.service.MeetingQueryService;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.web.bind.annotation.DeleteMapping;
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
    private final MeetingLifecycleService lifecycle;
    private final CurrentUserProvider currentUser;

    public MeetingController(MeetingQueryService query, MeetingCommandService command,
                             MeetingLifecycleService lifecycle, CurrentUserProvider currentUser) {
        this.query = query;
        this.command = command;
        this.lifecycle = lifecycle;
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

    @Operation(summary = "실시간 모임 시작 (방장)",
            description = "지금 모이는 모양(LIVE)을 방장이 시작한다. 대기실(OPEN)에서 방장 포함 2명 이상일 때만 되고, "
                    + "시작하면 status=IN_PROGRESS, phase=INTRO. 시작 후에는 신규 입장 불가·목록에서 빠지고 양털 추가 차감·환불은 없다. "
                    + "다른 참여자는 상세 조회(폴링)에서 status=IN_PROGRESS를 받으면 채팅방으로 이동한다. "
                    + "오류: MEETING_NOT_FOUND(404), NOT_HOST(403), NOT_LIVE_MEETING(400, 예약 모임), "
                    + "ALREADY_STARTED(409), NOT_ENOUGH_PARTICIPANTS(409, 방장 포함 2명 미만).")
    @PostMapping("/{meetingId}/start")
    public ApiResponse<StartMeetingResponse> start(@PathVariable long meetingId) {
        return ApiResponse.ok(lifecycle.start(currentUser.currentUserId(), meetingId));
    }

    @Operation(summary = "예약 모임 입장",
            description = "예약 모임(SCHEDULED)에 실제로 들어왔다고 서버에 알린다. 방장과 신청을 유지한 사람만, 시작 5분 전(상세의 enterOpenAt)부터. "
                    + "프론트는 5분 전 수동 입장, 1분 전 대기 화면을 켜 둔 사람은 자동으로 이 API를 부른다. 같은 사람이 다시 불러도 안전하다. "
                    + "서버는 예정 시각에 입장자 2명 이상이면 시작(방장이 없어도), 아니면 최대 2분 기다리다 2명 미만이면 자동 취소하고 "
                    + "참여자와 방장에게 양털을 전액 환불한다. 시작·취소·환불은 한 번만 처리된다. "
                    + "오류: MEETING_NOT_FOUND(404), NOT_JOINED(403), NOT_SCHEDULED_MEETING(400, 실시간 모임), "
                    + "ENTER_NOT_OPEN(409, 5분 전 이전), MEETING_NOT_JOINABLE(409, 취소·종료·대기 시간 초과, 또는 시작 후 처음 입장).")
    @PostMapping("/{meetingId}/enter")
    public ApiResponse<EnterMeetingResponse> enter(@PathVariable long meetingId) {
        return ApiResponse.ok(lifecycle.enter(currentUser.currentUserId(), meetingId));
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

    @Operation(summary = "모임 신청 취소 · 대기실 나가기",
            description = "시작 전(RECRUITING·OPEN) 모임에서 내 신청을 취소한다. 실시간은 시작 전이면 양털 1개 환불, 예약은 시작 10분 전"
                    + "(상세의 cancelRefundUntil)까지 환불하고 그 뒤에는 취소만 되고 환불은 없다. 취소한 모임에는 다시 신청할 수 없다. "
                    + "방장은 이 API 대신 모임 취소(DELETE /api/meetings/{id})를 쓴다. "
                    + "오류: MEETING_NOT_FOUND(404), NOT_JOINED(403), HOST_CANNOT_LEAVE(403), ALREADY_STARTED(409).")
    @DeleteMapping("/{meetingId}/participants/me")
    public ApiResponse<LeaveMeetingResponse> leave(@PathVariable long meetingId) {
        return ApiResponse.ok(command.leave(currentUser.currentUserId(), meetingId));
    }

    @Operation(summary = "모임 취소 · 삭제 (방장)",
            description = "방장이 시작 전 모임을 취소한다. 참여 중인 사람은 모두 양털 1개를 환불받는다. 방장 생성비는 예약 모임을 시작 10분 전까지 "
                    + "취소할 때만 환불하고, 실시간 모임은 환불하지 않는다. 성공하면 status는 CANCELED, cancelReason은 HOST_CANCELED. "
                    + "오류: MEETING_NOT_FOUND(404), NOT_HOST(403, 방장이 아님), ALREADY_STARTED(409, 이미 시작·종료·취소).")
    @DeleteMapping("/{meetingId}")
    public ApiResponse<CancelMeetingResponse> cancel(@PathVariable long meetingId) {
        return ApiResponse.ok(command.cancelMeeting(currentUser.currentUserId(), meetingId));
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
