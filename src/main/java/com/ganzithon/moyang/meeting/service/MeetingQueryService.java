package com.ganzithon.moyang.meeting.service;

import com.ganzithon.moyang.common.exception.BusinessException;
import com.ganzithon.moyang.common.exception.ErrorCode;
import com.ganzithon.moyang.meeting.domain.Meeting;
import com.ganzithon.moyang.meeting.domain.MeetingEnums.MeetingStatus;
import com.ganzithon.moyang.meeting.domain.MeetingEnums.MeetingType;
import com.ganzithon.moyang.meeting.domain.MeetingParticipant;
import com.ganzithon.moyang.meeting.dto.MeetingDtos.MeetingDetailResponse;
import com.ganzithon.moyang.meeting.dto.MeetingDtos.MeetingListResponse;
import com.ganzithon.moyang.meeting.dto.MeetingDtos.MeetingSummary;
import com.ganzithon.moyang.meeting.dto.MeetingDtos.MyMeetingItem;
import com.ganzithon.moyang.meeting.dto.MeetingDtos.MyMeetingsResponse;
import com.ganzithon.moyang.meeting.repository.MeetingParticipantRepository;
import com.ganzithon.moyang.meeting.repository.MeetingRepository;
import com.ganzithon.moyang.topic.domain.ThemeCode;
import com.ganzithon.moyang.user.repository.UserRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 모임 조회(목록 · 상세 · 내 모임). */
@Service
@Transactional(readOnly = true)
public class MeetingQueryService {

    static final ZoneId KST = ZoneId.of("Asia/Seoul");
    /** 목록에는 시작 전(모집 중·입장 가능) 모임만 나온다. 시작한 모임·종료·취소는 제외. */
    private static final Set<MeetingStatus> LISTED = EnumSet.of(MeetingStatus.RECRUITING, MeetingStatus.OPEN);
    /** '내 모임'에는 아직 끝나지 않은 모임이 나온다. */
    private static final Set<MeetingStatus> ACTIVE =
            EnumSet.of(MeetingStatus.RECRUITING, MeetingStatus.OPEN, MeetingStatus.IN_PROGRESS);

    private final MeetingRepository meetings;
    private final MeetingParticipantRepository participants;
    private final UserRepository users;
    private final Clock clock;

    public MeetingQueryService(MeetingRepository meetings, MeetingParticipantRepository participants,
                               UserRepository users, Clock clock) {
        this.meetings = meetings;
        this.participants = participants;
        this.users = users;
        this.clock = clock;
    }

    public LocalDateTime now() {
        return LocalDateTime.now(clock.withZone(KST));
    }

    public static OffsetDateTime toOffset(LocalDateTime time) {
        return time == null ? null : time.atZone(KST).toOffsetDateTime();
    }

    public MeetingListResponse list(long userId, String typeParam, String categoryParam) {
        MeetingType type = parseType(typeParam);
        String category = null;
        if (categoryParam != null && !categoryParam.isBlank()) {
            category = ThemeCode.of(categoryParam).orElseThrow(() -> new BusinessException(ErrorCode.INVALID_OPTION_CODE)).name();
        }
        LocalDateTime now = now();
        String wanted = category;
        List<Meeting> found = meetings.findByTypeAndStatusIn(type, LISTED).stream()
                .filter(m -> wanted == null || m.getCategory().equals(wanted))
                .filter(m -> LISTED.contains(m.statusAt(now)))
                .sorted(type == MeetingType.SCHEDULED
                        ? Comparator.comparing(Meeting::getStartAt).thenComparing(Meeting::getId)
                        : Comparator.comparing(Meeting::getCreatedAt).reversed().thenComparing(Meeting::getId))
                .toList();

        Map<Long, Long> counts = countsOf(found);
        Set<Long> joined = joinedIds(found, userId);
        List<MeetingSummary> items = found.stream().map(m -> new MeetingSummary(m.getId(), m.getType().name(),
                m.getTitle(), MeetingDefaults.summaryOf(m), m.getCategory(), toOffset(m.getStartAt()),
                m.getCapacity(), counts.getOrDefault(m.getId(), 0L).intValue(), m.statusAt(now).name(),
                joined.contains(m.getId()))).toList();

        // '오늘의 이야기와 가장 가까운 모임': 내 고민과 같은 카테고리 중 가장 빨리 시작하는 예약 모임
        String myTheme = users.findById(userId).map(u -> u.getThemeCode()).orElse(null);
        Long recommended = myTheme == null || type != MeetingType.SCHEDULED ? null
                : found.stream().filter(m -> m.getCategory().equals(myTheme)).map(Meeting::getId).findFirst().orElse(null);
        return new MeetingListResponse(recommended, items);
    }

    public MeetingDetailResponse detail(long userId, long meetingId) {
        LocalDateTime now = now();
        Meeting m = meetings.findById(meetingId).filter(x -> !x.expired(now))
                .orElseThrow(() -> new BusinessException(ErrorCode.MEETING_NOT_FOUND));
        MeetingStatus status = m.statusAt(now);
        long count = participants.countByMeetingId(meetingId);
        MeetingParticipant mine = participants.findByMeetingIdAndUserId(meetingId, userId).orElse(null);
        boolean isHost = m.getHostUserId() == userId;
        boolean scheduled = m.getType() == MeetingType.SCHEDULED;
        // 실시간은 바로 주제 공개, 예약은 시작(진행 중)된 뒤에 공개
        boolean topicVisible = !scheduled || status == MeetingStatus.IN_PROGRESS || status == MeetingStatus.ENDED;
        return new MeetingDetailResponse(m.getId(), m.getType().name(), m.getTitle(), m.getDescription(),
                m.getCategory(), MeetingDefaults.tagsOf(m), topicVisible ? m.getTopicTitle() : null,
                toOffset(m.getStartAt()),
                scheduled ? toOffset(m.getStartAt().plus(Meeting.SCHEDULED_LENGTH)) : null,
                m.getCapacity(), MeetingDefaults.MIN_PARTICIPANTS, (int) count, status.name(), mine != null,
                mine == null ? null : mine.getMeetingNickname(), MeetingDefaults.STEPS,
                MeetingDefaults.RECOMMENDED_FOR, m.getHostUserId(), isHost,
                // 시작 권한은 실시간 모임의 방장만, 대기실(OPEN)에서 방장 포함 2명 이상일 때
                !scheduled && isHost && status == MeetingStatus.OPEN && count >= MeetingDefaults.MIN_PARTICIPANTS,
                // 주제 건너뛰기 권한: 진행 중일 때 방장 (방장 없이 시작한 모임의 '첫 입장자' 규칙은 시작 단위에서 추가)
                status == MeetingStatus.IN_PROGRESS && isHost,
                scheduled ? toOffset(m.getStartAt().minus(Meeting.ENTER_OPEN_BEFORE)) : null,
                scheduled ? toOffset(m.getStartAt().minus(Meeting.REFUND_DEADLINE_BEFORE)) : null,
                m.getCancelReason() == null ? null : m.getCancelReason().name());
    }

    public MyMeetingsResponse mine(long userId) {
        LocalDateTime now = now();
        Map<Long, MeetingParticipant> mine = participants.findByUserId(userId).stream()
                .collect(Collectors.toMap(MeetingParticipant::getMeetingId, Function.identity()));
        List<Meeting> found = meetings.findAllById(mine.keySet()).stream()
                .filter(m -> ACTIVE.contains(m.getStatus()))
                .sorted(Comparator.comparing((Meeting m) -> m.getStartAt() == null ? m.getCreatedAt() : m.getStartAt())
                        .thenComparing(Meeting::getId))
                .toList();
        Map<Long, Long> counts = countsOf(found);
        return new MyMeetingsResponse(found.stream().map(m -> new MyMeetingItem(m.getId(), m.getType().name(),
                m.getTitle(), MeetingDefaults.summaryOf(m), m.getCategory(), toOffset(m.getStartAt()),
                m.getCapacity(), counts.getOrDefault(m.getId(), 0L).intValue(), m.statusAt(now).name(),
                m.getHostUserId() == userId, mine.get(m.getId()).getMeetingNickname())).toList());
    }

    private Map<Long, Long> countsOf(List<Meeting> found) {
        if (found.isEmpty()) {
            return Map.of();
        }
        return participants.countByMeetingIds(found.stream().map(Meeting::getId).toList()).stream()
                .collect(Collectors.toMap(c -> c.getMeetingId(), c -> c.getCnt()));
    }

    private Set<Long> joinedIds(List<Meeting> found, long userId) {
        if (found.isEmpty()) {
            return Set.of();
        }
        return participants.findByMeetingIdInAndUserId(found.stream().map(Meeting::getId).toList(), userId).stream()
                .map(MeetingParticipant::getMeetingId).collect(Collectors.toSet());
    }

    private static MeetingType parseType(String type) {
        try {
            return MeetingType.valueOf(type);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
    }
}
