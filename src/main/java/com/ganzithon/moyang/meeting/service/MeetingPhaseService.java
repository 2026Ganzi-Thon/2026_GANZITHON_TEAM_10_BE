package com.ganzithon.moyang.meeting.service;

import com.ganzithon.moyang.common.exception.BusinessException;
import com.ganzithon.moyang.common.exception.ErrorCode;
import com.ganzithon.moyang.meeting.domain.Meeting;
import com.ganzithon.moyang.meeting.domain.MeetingEnums.MeetingPhase;
import com.ganzithon.moyang.meeting.domain.MeetingEnums.MeetingStatus;
import com.ganzithon.moyang.meeting.domain.MeetingMessage;
import com.ganzithon.moyang.meeting.domain.MeetingParticipant;
import com.ganzithon.moyang.meeting.dto.MeetingDtos.PhaseNextResponse;
import com.ganzithon.moyang.meeting.repository.MeetingMessageRepository;
import com.ganzithon.moyang.meeting.repository.MeetingParticipantRepository;
import com.ganzithon.moyang.meeting.repository.MeetingRepository;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 모임 진행 단계(INTRO → DRAWING → SHARING → CLOSING)를 넘기고 단계 안내(시스템 메시지)를 남긴다.
 * 단계는 항상 모임 행 락 안에서만 바꾼다. 그래서 "그림이 다 모여 자동 진행"과 "방장이 버튼"이 동시에 와도 한 번에 한 단계씩 처리된다.
 * {@link #announceStart}와 {@link #advance}는 부르는 쪽의 트랜잭션(모임 행 락을 이미 잡은 상태) 안에서 쓴다.
 */
@Service
public class MeetingPhaseService {

    private final MeetingRepository meetings;
    private final MeetingParticipantRepository participants;
    private final MeetingMessageRepository messages;
    private final MeetingQueryService time;
    private final MeetingRoster roster;

    public MeetingPhaseService(MeetingRepository meetings, MeetingParticipantRepository participants,
                               MeetingMessageRepository messages, MeetingQueryService time, MeetingRoster roster) {
        this.meetings = meetings;
        this.participants = participants;
        this.messages = messages;
        this.time = time;
        this.roster = roster;
    }

    /** 방장(또는 예약 모임에서 방장이 없을 때 첫 입장자)이 다음 단계로 넘긴다(주제 건너뛰기 버튼). */
    @Transactional
    public PhaseNextResponse next(long userId, long meetingId) {
        LocalDateTime now = time.now();
        Meeting meeting = meetings.findForUpdate(meetingId).filter(m -> !m.expired(now))
                .orElseThrow(() -> new BusinessException(ErrorCode.MEETING_NOT_FOUND));
        MeetingParticipant mine = participants.findByMeetingIdAndUserId(meetingId, userId)
                .filter(MeetingParticipant::isActive)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_PARTICIPANT));
        if (!roster.hasRoomAccess(meeting, mine)) {
            throw new BusinessException(ErrorCode.NOT_PARTICIPANT);
        }
        if (meeting.getStatus() != MeetingStatus.IN_PROGRESS) {
            throw new BusinessException(ErrorCode.MEETING_NOT_IN_PROGRESS);
        }
        if (time.skipperUserId(meeting) != userId) {
            throw new BusinessException(ErrorCode.NO_SKIP_PERMISSION);
        }
        if (meeting.getPhase() == null || meeting.getPhase().isLast()) {
            throw new BusinessException(ErrorCode.LAST_PHASE);
        }
        advance(meeting, now);
        return new PhaseNextResponse(meetingId, meeting.getPhase().name(), meeting.getTopicTitle());
    }

    /** 모임이 시작될 때(INTRO) 안내 한 줄을 남긴다. 시작은 한 번만 일어나므로 안내도 한 번만 남는다. */
    void announceStart(Meeting meeting, LocalDateTime now) {
        messages.saveAndFlush(MeetingMessage.system(meeting.getId(), phaseMessage(MeetingPhase.INTRO, meeting), now));
    }

    /** 단계를 하나 넘기고 안내를 남긴다. 마지막 단계면 아무것도 하지 않는다. */
    void advance(Meeting meeting, LocalDateTime now) {
        if (!meeting.advancePhase()) {
            return;
        }
        meetings.saveAndFlush(meeting);
        messages.saveAndFlush(MeetingMessage.system(meeting.getId(), phaseMessage(meeting.getPhase(), meeting), now));
    }

    /** 단계 안내 고정 문구(초안: 기획 검토 필요). */
    static String phaseMessage(MeetingPhase phase, Meeting meeting) {
        return switch (phase) {
            case INTRO -> "모임이 시작됐어요. 먼저 가볍게 자기소개를 나눠 보세요.";
            case DRAWING -> "오늘의 주제는 '" + meeting.getTopicTitle() + "'예요. 주제에 맞게 그림을 그려 올려 주세요.";
            case SHARING -> "이제 그림에 담긴 이야기를 서로 나눠 보세요.";
            case CLOSING -> "마무리 시간이에요. 오늘의 이야기를 한마디씩 정리해 보세요.";
        };
    }
}
