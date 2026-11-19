package com.ganzithon.moyang.meeting.service;

import com.ganzithon.moyang.common.exception.BusinessException;
import com.ganzithon.moyang.common.exception.ErrorCode;
import com.ganzithon.moyang.meeting.domain.Meeting;
import com.ganzithon.moyang.meeting.domain.MeetingEnums.MeetingType;
import com.ganzithon.moyang.meeting.domain.MeetingParticipant;
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
        if (participants.findByMeetingIdAndUserId(meetingId, userId).isPresent()) {
            throw new BusinessException(ErrorCode.ALREADY_JOINED);
        }
        // 시작 후(진행 중)·종료·취소된 모임, 예약 시각이 지난 모임은 신규 입장 불가
        MeetingStatus status = meeting.statusAt(now);
        boolean pastStart = meeting.getType() == MeetingType.SCHEDULED && !now.isBefore(meeting.getStartAt());
        if ((status != MeetingStatus.RECRUITING && status != MeetingStatus.OPEN) || pastStart) {
            throw new BusinessException(ErrorCode.MEETING_NOT_JOINABLE);
        }
        long count = participants.countByMeetingId(meetingId);
        if (count >= meeting.getCapacity()) {
            throw new BusinessException(ErrorCode.MEETING_FULL);
        }

        participants.saveAndFlush(new MeetingParticipant(meetingId, userId, nickname, now));
        SheepChangeResponse paid = sheep.useMeetingJoin(userId, meetingId);
        return new JoinMeetingResponse(meetingId, (int) count + 1, meeting.getCapacity(), status.name(), nickname,
                paid.applied() ? 1 : 0, paid.balance());
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
