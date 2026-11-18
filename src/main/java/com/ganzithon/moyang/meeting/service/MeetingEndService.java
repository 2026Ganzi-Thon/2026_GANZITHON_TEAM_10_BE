package com.ganzithon.moyang.meeting.service;

import com.ganzithon.moyang.common.exception.BusinessException;
import com.ganzithon.moyang.common.exception.ErrorCode;
import com.ganzithon.moyang.meeting.domain.Meeting;
import com.ganzithon.moyang.meeting.domain.MeetingEnums.MeetingStatus;
import com.ganzithon.moyang.meeting.domain.MeetingMessage;
import com.ganzithon.moyang.meeting.domain.MeetingParticipant;
import com.ganzithon.moyang.meeting.dto.MeetingDtos.EndMeetingResponse;
import com.ganzithon.moyang.meeting.repository.MeetingMessageRepository;
import com.ganzithon.moyang.meeting.repository.MeetingParticipantRepository;
import com.ganzithon.moyang.meeting.repository.MeetingRepository;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 대화 종료 동의. 참여 중인 사람 전원이 누르면 모임이 종료(ENDED)된다.
 * 동의 기록과 종료는 모임 행 락 안에서 한 트랜잭션으로 처리하고(마지막 두 명이 동시에 눌러도 종료는 한 번),
 * 회고 카드는 그 트랜잭션이 <b>커밋된 뒤에</b> 트랜잭션 밖에서 따로 시작한다(실패해도 종료는 되돌아가지 않는다).
 */
@Service
public class MeetingEndService {

    static final String ENDED_MESSAGE = "모두 동의해서 모임이 끝났어요.";

    private final MeetingRepository meetings;
    private final MeetingParticipantRepository participants;
    private final MeetingMessageRepository messages;
    private final MeetingQueryService time;
    private final MeetingCardTrigger cardTrigger;
    private final TransactionTemplate transaction;

    public MeetingEndService(MeetingRepository meetings, MeetingParticipantRepository participants,
                             MeetingMessageRepository messages, MeetingQueryService time,
                             MeetingCardTrigger cardTrigger, PlatformTransactionManager tm) {
        this.meetings = meetings;
        this.participants = participants;
        this.messages = messages;
        this.time = time;
        this.cardTrigger = cardTrigger;
        this.transaction = new TransactionTemplate(tm);
    }

    private record Outcome(EndMeetingResponse response, boolean justEnded) {}

    /**
     * 검사 순서: 없는 모임(종료 24시간 뒤 포함) 404 → 참여자 아님 403(NOT_JOINED) → 진행 중 아님 409.
     * 이미 종료된 모임에서 내가 동의한 기록이 있으면 오류 없이 현재 상태를 돌려준다(마지막 사람이 두 번 눌러도 안전).
     */
    public EndMeetingResponse end(long userId, long meetingId) {
        Outcome outcome = transaction.execute(status -> endInTransaction(userId, meetingId));
        if (outcome.justEnded()) {
            cardTrigger.generateAfterEnd(meetingId); // 커밋이 끝난 뒤, 트랜잭션 밖
        }
        return outcome.response();
    }

    private Outcome endInTransaction(long userId, long meetingId) {
        LocalDateTime now = time.now();
        Meeting meeting = meetings.findForUpdate(meetingId).filter(m -> !m.expired(now))
                .orElseThrow(() -> new BusinessException(ErrorCode.MEETING_NOT_FOUND));
        MeetingParticipant mine = participants.findByMeetingIdAndUserId(meetingId, userId)
                .filter(MeetingParticipant::isActive)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_JOINED));
        if (meeting.getStatus() == MeetingStatus.ENDED && mine.hasAgreedEnd()) {
            return new Outcome(response(meeting), false);
        }
        if (meeting.getStatus() != MeetingStatus.IN_PROGRESS) {
            throw new BusinessException(ErrorCode.MEETING_NOT_IN_PROGRESS);
        }
        mine.agreeEnd(now);
        participants.saveAndFlush(mine);

        long total = participants.countActiveByMeetingId(meetingId);
        long agreed = participants.countByMeetingIdAndCanceledAtIsNullAndEndAgreedAtIsNotNull(meetingId);
        boolean everyoneAgreed = agreed >= total;
        if (everyoneAgreed) {
            meeting.end(now);
            meetings.saveAndFlush(meeting);
            messages.saveAndFlush(MeetingMessage.system(meetingId, ENDED_MESSAGE, now));
        }
        return new Outcome(response(meeting), everyoneAgreed);
    }

    private EndMeetingResponse response(Meeting meeting) {
        long id = meeting.getId();
        return new EndMeetingResponse(id,
                (int) participants.countByMeetingIdAndCanceledAtIsNullAndEndAgreedAtIsNotNull(id),
                (int) participants.countActiveByMeetingId(id), meeting.getStatus().name());
    }
}
