package com.ganzithon.moyang.meeting.service;

import com.ganzithon.moyang.common.exception.BusinessException;
import com.ganzithon.moyang.common.exception.ErrorCode;
import com.ganzithon.moyang.meeting.domain.Meeting;
import com.ganzithon.moyang.meeting.domain.MeetingEnums.CancelReason;
import com.ganzithon.moyang.meeting.domain.MeetingEnums.MeetingStatus;
import com.ganzithon.moyang.meeting.domain.MeetingEnums.MeetingType;
import com.ganzithon.moyang.meeting.domain.MeetingParticipant;
import com.ganzithon.moyang.meeting.dto.MeetingDtos.EnterMeetingResponse;
import com.ganzithon.moyang.meeting.dto.MeetingDtos.StartMeetingResponse;
import com.ganzithon.moyang.meeting.repository.MeetingParticipantRepository;
import com.ganzithon.moyang.meeting.repository.MeetingRepository;
import com.ganzithon.moyang.sheep.service.SheepService;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 모임 시작·입장·자동 처리. 락 순서는 항상 "모임 행 → 양털 지갑"이다.
 * 시작과 자동 취소는 모임 행 락 안에서 현재 상태를 다시 확인하고 처리하므로, 같은 모임이 두 번 시작되거나
 * 두 번 환불되지 않는다(스케줄러를 여러 번 돌려도, 새로 만들어도 안전).
 */
@Service
public class MeetingLifecycleService {

    private static final Logger log = LoggerFactory.getLogger(MeetingLifecycleService.class);
    private static final int MIN_TO_START = MeetingDefaults.MIN_PARTICIPANTS;

    private final MeetingRepository meetings;
    private final MeetingParticipantRepository participants;
    private final SheepService sheep;
    private final MeetingQueryService time;
    private final TransactionTemplate transaction;

    public MeetingLifecycleService(MeetingRepository meetings, MeetingParticipantRepository participants,
                                   SheepService sheep, MeetingQueryService time, PlatformTransactionManager tm) {
        this.meetings = meetings;
        this.participants = participants;
        this.sheep = sheep;
        this.time = time;
        this.transaction = new TransactionTemplate(tm);
    }

    /** 실시간 모임 시작. 방장만, 대기실(OPEN)에서 방장 포함 2명 이상일 때. 시작 후에는 추가 차감·환불이 없다. */
    @Transactional
    public StartMeetingResponse start(long userId, long meetingId) {
        Meeting meeting = meetings.findForUpdate(meetingId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEETING_NOT_FOUND));
        if (meeting.getHostUserId() != userId) {
            throw new BusinessException(ErrorCode.NOT_HOST);
        }
        if (meeting.getType() != MeetingType.LIVE) {
            throw new BusinessException(ErrorCode.NOT_LIVE_MEETING);
        }
        if (meeting.getStatus() != MeetingStatus.OPEN) {
            throw new BusinessException(ErrorCode.ALREADY_STARTED);
        }
        long count = participants.countActiveByMeetingId(meetingId);
        if (count < MIN_TO_START) {
            throw new BusinessException(ErrorCode.NOT_ENOUGH_PARTICIPANTS);
        }
        meeting.start(time.now());
        meetings.saveAndFlush(meeting);
        return new StartMeetingResponse(meetingId, meeting.getStatus().name(), meeting.getPhase().name(), (int) count);
    }

    /**
     * 예약 모임 입장 기록. 방장과 신청을 유지한 사람만, 시작 5분 전부터. 같은 사람이 다시 눌러도 안전하다.
     * 시작 후에는 이미 입장한 사람만 다시 들어올 수 있다. 시작 시각 + 2분이 지나도록 시작되지 않은 모임은 들어올 수 없다.
     * 입장으로 2명이 모였고 시작 시각이 지났다면 바로 시작한다.
     */
    @Transactional
    public EnterMeetingResponse enter(long userId, long meetingId) {
        Meeting meeting = meetings.findForUpdate(meetingId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEETING_NOT_FOUND));
        MeetingParticipant mine = participants.findByMeetingIdAndUserId(meetingId, userId)
                .filter(MeetingParticipant::isActive)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_JOINED));
        if (meeting.getType() != MeetingType.SCHEDULED) {
            throw new BusinessException(ErrorCode.NOT_SCHEDULED_MEETING);
        }
        LocalDateTime now = time.now();
        MeetingStatus status = meeting.getStatus();
        if (status == MeetingStatus.CANCELED || status == MeetingStatus.ENDED
                || (status == MeetingStatus.IN_PROGRESS && !mine.hasEntered())) {
            throw new BusinessException(ErrorCode.MEETING_NOT_JOINABLE);
        }
        if (status == MeetingStatus.RECRUITING) {
            if (now.isBefore(meeting.getStartAt().minus(Meeting.ENTER_OPEN_BEFORE))) {
                throw new BusinessException(ErrorCode.ENTER_NOT_OPEN);
            }
            if (waitExpired(meeting, now)) {
                // 기다려 주는 시간이 지났다: 곧 스케줄러가 자동 취소한다. 지금 들어와도 살릴 수 없다.
                throw new BusinessException(ErrorCode.MEETING_NOT_JOINABLE);
            }
            mine.enter(now);
            participants.saveAndFlush(mine);
            decide(meeting, now);
        }
        int entered = (int) participants.countByMeetingIdAndCanceledAtIsNullAndEnteredAtIsNotNull(meetingId);
        MeetingStatus after = meeting.statusAt(now);
        return new EnterMeetingResponse(meetingId, after.name(),
                meeting.getPhase() == null ? null : meeting.getPhase().name(), entered,
                MeetingQueryService.toOffset(meeting.getStartAt()));
    }

    /**
     * 시작 시각이 지난 예약 모임을 처리한다(스케줄러가 부른다). 모임마다 따로 트랜잭션을 열고, 한 모임이 실패해도
     * 로그만 남기고 다음 모임을 계속 처리한다. 실패한 모임은 다음 회차에 다시 시도된다.
     */
    public void processDue(LocalDateTime now) {
        List<Long> dueIds = meetings.findDueIds(MeetingType.SCHEDULED, MeetingStatus.RECRUITING, now);
        for (Long id : dueIds) {
            try {
                transaction.executeWithoutResult(s -> processOne(id, now));
            } catch (RuntimeException e) {
                log.warn("예약 모임 {} 자동 처리에 실패했어요. 다음 회차에 다시 시도해요.", id, e);
            }
        }
    }

    /** 락을 잡고 지금 상태를 다시 확인한 뒤, 아직 RECRUITING인 모임만 시작 또는 자동 취소한다. */
    private void processOne(long meetingId, LocalDateTime now) {
        Meeting meeting = meetings.findForUpdate(meetingId).orElse(null);
        if (meeting == null || meeting.getStatus() != MeetingStatus.RECRUITING
                || meeting.getType() != MeetingType.SCHEDULED || now.isBefore(meeting.getStartAt())) {
            return; // 이미 시작·취소됐거나 아직 시간이 안 된 모임은 건드리지 않는다
        }
        decide(meeting, now);
    }

    /**
     * 시작 시각이 지난 RECRUITING 예약 모임의 운명을 정한다. 시각 계산만 쓴다(메모리 타이머 없음).
     * 입장자 2명 이상 → 시작 / 2명 미만이고 시작 시각 + 2분이 지남 → 자동 취소·환불 / 그 외 → 계속 기다림.
     */
    private void decide(Meeting meeting, LocalDateTime now) {
        if (now.isBefore(meeting.getStartAt())) {
            return;
        }
        long entered = participants.countByMeetingIdAndCanceledAtIsNullAndEnteredAtIsNotNull(meeting.getId());
        if (entered >= MIN_TO_START) {
            meeting.start(now);
            meetings.saveAndFlush(meeting);
        } else if (waitExpired(meeting, now)) {
            cancelForLackOfParticipants(meeting, now);
        }
    }

    /** 입장자가 모자라 자동 취소: 신청을 유지한 참여자와 방장 모두에게 양털을 전액 환불한다. */
    private void cancelForLackOfParticipants(Meeting meeting, LocalDateTime now) {
        List<MeetingParticipant> everyone = participants.findByMeetingIdAndCanceledAtIsNull(meeting.getId()).stream()
                .sorted(Comparator.comparingLong(MeetingParticipant::getUserId)) // 지갑 락 순서를 고정해 교착 방지
                .toList();
        for (MeetingParticipant p : everyone) {
            sheep.refundMeeting(p.getUserId(), meeting.getId());
        }
        meeting.cancel(CancelReason.NOT_ENOUGH_PARTICIPANTS, now);
        meetings.saveAndFlush(meeting);
    }

    private static boolean waitExpired(Meeting meeting, LocalDateTime now) {
        return !now.isBefore(meeting.getStartAt().plus(Meeting.START_WAIT));
    }
}
