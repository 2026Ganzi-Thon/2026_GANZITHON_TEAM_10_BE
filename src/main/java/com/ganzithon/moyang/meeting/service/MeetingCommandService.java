package com.ganzithon.moyang.meeting.service;

import com.ganzithon.moyang.common.exception.BusinessException;
import com.ganzithon.moyang.common.exception.ErrorCode;
import com.ganzithon.moyang.meeting.domain.Meeting;
import com.ganzithon.moyang.meeting.domain.MeetingEnums.MeetingType;
import com.ganzithon.moyang.meeting.domain.MeetingParticipant;
import com.ganzithon.moyang.meeting.domain.MeetingEnums.CancelReason;
import com.ganzithon.moyang.meeting.dto.MeetingDtos.CancelMeetingResponse;
import com.ganzithon.moyang.meeting.dto.MeetingDtos.LeaveMeetingResponse;
import com.ganzithon.moyang.meeting.dto.MeetingDtos.CreateMeetingRequest;
import com.ganzithon.moyang.meeting.dto.MeetingDtos.CreateMeetingResponse;
import com.ganzithon.moyang.meeting.dto.MeetingDtos.JoinMeetingResponse;
import com.ganzithon.moyang.meeting.domain.MeetingEnums.MeetingStatus;
import com.ganzithon.moyang.meeting.repository.MeetingParticipantRepository;
import com.ganzithon.moyang.meeting.repository.MeetingRepository;
import com.ganzithon.moyang.sheep.dto.SheepChangeResponse;
import com.ganzithon.moyang.sheep.service.SheepService;
import com.ganzithon.moyang.topic.domain.ThemeCode;
import com.ganzithon.moyang.topic.domain.Topic;
import com.ganzithon.moyang.topic.repository.TopicRepository;
import com.ganzithon.moyang.user.domain.User;
import com.ganzithon.moyang.user.repository.UserRepository;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 모임 만들기 등 상태를 바꾸는 작업.
 * 락 순서는 항상 "모임 행 → 양털 지갑"이다(교착 방지). 만들기는 모임을 먼저 저장(새 행)한 뒤 양털을 차감한다.
 */
@Service
public class MeetingCommandService {

    private static final Set<Integer> CAPACITIES = Set.of(2, 4, 6);
    private static final int TITLE_MAX = 30;
    private static final int DESCRIPTION_MAX = 200;
    private static final int NICKNAME_MIN = 2;
    private static final int NICKNAME_MAX = 15;
    private static final int MAX_DAYS_AHEAD = 7;

    private final MeetingRepository meetings;
    private final MeetingParticipantRepository participants;
    private final UserRepository users;
    private final TopicRepository topics;
    private final SheepService sheep;
    private final MeetingQueryService time;

    public MeetingCommandService(MeetingRepository meetings, MeetingParticipantRepository participants,
                                 UserRepository users, TopicRepository topics, SheepService sheep,
                                 MeetingQueryService time) {
        this.meetings = meetings;
        this.participants = participants;
        this.users = users;
        this.topics = topics;
        this.sheep = sheep;
        this.time = time;
    }

    /** 모임 저장 + 방장 참여자 저장 + 양털 -1을 한 트랜잭션으로. 어느 하나라도 실패하면 전부 되돌린다. */
    @Transactional
    public CreateMeetingResponse create(long userId, CreateMeetingRequest req) {
        MeetingType type = parseType(req.type());
        String title = requireText(req.title(), TITLE_MAX);
        String description = optionalText(req.description(), DESCRIPTION_MAX);
        ThemeCode category = ThemeCode.of(require(req.category()))
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_OPTION_CODE));
        int capacity = parseCapacity(req.capacity());
        LocalDateTime now = time.now();
        LocalDateTime startAt = type == MeetingType.SCHEDULED ? parseStartAt(req.startAt(), now) : null;

        User host = users.findById(userId).orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
        String nickname = parseNickname(req.meetingNickname(), host.getNickname());

        Meeting meeting = meetings.saveAndFlush(new Meeting(type, title, description, category.name(), startAt,
                capacity, userId, pickTopicTitle(category), now));
        participants.save(new MeetingParticipant(meeting.getId(), userId, nickname, now));

        SheepChangeResponse paid = sheep.useMeetingCreate(userId, meeting.getId());
        return new CreateMeetingResponse(meeting.getId(), meeting.getStatus().name(), 1, 1, paid.balance());
    }

    /**
     * 신청 · 대기실 입장. 락 순서: 모임 행 → (참여자 저장) → 양털 지갑. 정원 확인과 저장이 모임 행 락 안에서 이뤄져
     * 동시에 눌러도 정원을 넘지 않는다. 양털이 모자라면(NOT_ENOUGH_WOOL) 참여 저장도 함께 되돌아간다.
     */
    @Transactional
    public JoinMeetingResponse join(long userId, long meetingId, String meetingNicknameParam) {
        User user = users.findById(userId).orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
        String nickname = parseNickname(meetingNicknameParam, user.getNickname());

        Meeting meeting = meetings.findForUpdate(meetingId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEETING_NOT_FOUND));
        LocalDateTime now = time.now();
        var existing = participants.findByMeetingIdAndUserId(meetingId, userId);
        if (existing.isPresent()) {
            // 취소한 사람은 같은 모임에 다시 신청할 수 없다(양털 내역이 모임당 1번만 차감이라 공짜 입장을 막기 위해)
            throw new BusinessException(existing.get().isActive() ? ErrorCode.ALREADY_JOINED : ErrorCode.ALREADY_CANCELED);
        }
        // 시작 후(진행 중)·종료·취소된 모임, 예약 시각이 지난 모임은 신규 입장 불가
        MeetingStatus status = meeting.statusAt(now);
        boolean pastStart = meeting.getType() == MeetingType.SCHEDULED && !now.isBefore(meeting.getStartAt());
        if ((status != MeetingStatus.RECRUITING && status != MeetingStatus.OPEN) || pastStart) {
            throw new BusinessException(ErrorCode.MEETING_NOT_JOINABLE);
        }
        long count = participants.countActiveByMeetingId(meetingId);
        if (count >= meeting.getCapacity()) {
            throw new BusinessException(ErrorCode.MEETING_FULL);
        }

        participants.saveAndFlush(new MeetingParticipant(meetingId, userId, nickname, now));
        SheepChangeResponse paid = sheep.useMeetingJoin(userId, meetingId);
        return new JoinMeetingResponse(meetingId, (int) count + 1, meeting.getCapacity(), status.name(), nickname,
                paid.applied() ? 1 : 0, paid.balance());
    }

    /**
     * 참여자 신청 취소 · 대기실 나가기. 시작 전(RECRUITING·OPEN)에만 가능하고, 방장은 모임 취소를 써야 한다.
     * 환불: 실시간은 시작 전이면 환불, 예약은 시작 10분 전까지 환불(이후엔 취소만 되고 환불 없음).
     * 취소 표시와 환불은 한 트랜잭션이고, 락 순서는 모임 행 → 양털 지갑이다.
     */
    @Transactional
    public LeaveMeetingResponse leave(long userId, long meetingId) {
        Meeting meeting = meetings.findForUpdate(meetingId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEETING_NOT_FOUND));
        MeetingParticipant mine = participants.findByMeetingIdAndUserId(meetingId, userId)
                .filter(MeetingParticipant::isActive)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_PARTICIPANT));
        if (meeting.getHostUserId() == userId) {
            throw new BusinessException(ErrorCode.HOST_CANNOT_LEAVE);
        }
        LocalDateTime now = time.now();
        requireCancelable(meeting, now);

        boolean refund = guestRefundable(meeting, now);
        mine.cancel(now);
        participants.saveAndFlush(mine);
        int refunded = refund && sheep.refundMeeting(userId, meetingId).applied() ? 1 : 0;
        int balance = sheep.balance(userId).balance();
        return new LeaveMeetingResponse(meetingId, (int) participants.countActiveByMeetingId(meetingId),
                refunded == 1, refunded, balance);
    }

    /**
     * 방장 모임 취소 · 삭제. 시작 전에만 가능. 참여 중인 사람은 모두 환불한다.
     * 방장 생성비는 예약 모임을 시작 10분 전까지 취소할 때만 환불하고, 실시간 모임은 환불하지 않는다.
     * 여러 지갑을 잠그므로 참여자를 userId 순서로 처리해 다른 취소와 교착이 나지 않게 한다.
     */
    @Transactional
    public CancelMeetingResponse cancelMeeting(long userId, long meetingId) {
        Meeting meeting = meetings.findForUpdate(meetingId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEETING_NOT_FOUND));
        if (meeting.getHostUserId() != userId) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        LocalDateTime now = time.now();
        requireCancelable(meeting, now);

        int refundedParticipants = 0;
        List<MeetingParticipant> guests = participants.findByMeetingIdAndCanceledAtIsNull(meetingId).stream()
                .filter(p -> p.getUserId() != userId)
                .sorted(Comparator.comparingLong(MeetingParticipant::getUserId))
                .toList();
        for (MeetingParticipant guest : guests) {
            if (sheep.refundMeeting(guest.getUserId(), meetingId).applied()) {
                refundedParticipants++;
            }
        }
        boolean hostRefunded = hostRefundable(meeting, now) && sheep.refundMeeting(userId, meetingId).applied();
        meeting.cancel(CancelReason.HOST_CANCELED, now);
        meetings.saveAndFlush(meeting);
        return new CancelMeetingResponse(meetingId, meeting.getStatus().name(), refundedParticipants, hostRefunded,
                sheep.balance(userId).balance());
    }

    /** 시작 전(모집 중·입장 가능)이 아니면 취소할 수 없다. 진행 중·종료·이미 취소된 모임은 거절. */
    private static void requireCancelable(Meeting meeting, LocalDateTime now) {
        MeetingStatus status = meeting.statusAt(now);
        if (status != MeetingStatus.RECRUITING && status != MeetingStatus.OPEN) {
            throw new BusinessException(ErrorCode.MEETING_NOT_CANCELABLE);
        }
    }

    /** 참여자 환불: 실시간은 시작 전이면 항상, 예약은 시작 10분 전까지. */
    private static boolean guestRefundable(Meeting meeting, LocalDateTime now) {
        return meeting.getType() == MeetingType.LIVE || beforeRefundDeadline(meeting, now);
    }

    /** 방장 생성비 환불: 예약 모임을 시작 10분 전까지 취소할 때만. */
    private static boolean hostRefundable(Meeting meeting, LocalDateTime now) {
        return meeting.getType() == MeetingType.SCHEDULED && beforeRefundDeadline(meeting, now);
    }

    private static boolean beforeRefundDeadline(Meeting meeting, LocalDateTime now) {
        return !now.isAfter(meeting.getStartAt().minus(Meeting.REFUND_DEADLINE_BEFORE));
    }

    private String pickTopicTitle(ThemeCode category) {
        return topics.findByThemeCodeOrderByIdAsc(category.topicGroup().name()).stream()
                .findFirst().map(Topic::getTitle).orElse(null);
    }

    private static MeetingType parseType(String type) {
        try {
            return MeetingType.valueOf(require(type));
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
    }

    private static String require(String value) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        return value.trim();
    }

    private static String requireText(String value, int max) {
        String text = require(value);
        if (text.length() > max) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        return text;
    }

    private static String optionalText(String value, int max) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String text = value.trim();
        if (text.length() > max) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        return text;
    }

    private static int parseCapacity(Integer capacity) {
        if (capacity == null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        if (!CAPACITIES.contains(capacity)) {
            throw new BusinessException(ErrorCode.INVALID_CAPACITY);
        }
        return capacity;
    }

    /** 예약은 startAt 필수. 지금 이후, 최대 7일 뒤까지. */
    private static LocalDateTime parseStartAt(OffsetDateTime startAt, LocalDateTime now) {
        if (startAt == null) {
            throw new BusinessException(ErrorCode.START_AT_REQUIRED);
        }
        LocalDateTime local = startAt.atZoneSameInstant(MeetingQueryService.KST).toLocalDateTime();
        if (!local.isAfter(now) || local.isAfter(now.plusDays(MAX_DAYS_AHEAD))) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        return local;
    }

    /** 모임 닉네임(2~15자). 생략하면 내 양 닉네임. */
    static String parseNickname(String nickname, String fallback) {
        if (nickname == null || nickname.isBlank()) {
            return fallback;
        }
        String text = nickname.trim();
        if (text.length() < NICKNAME_MIN || text.length() > NICKNAME_MAX) {
            throw new BusinessException(ErrorCode.INVALID_NICKNAME);
        }
        return text;
    }
}
