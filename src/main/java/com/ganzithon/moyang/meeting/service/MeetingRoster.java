package com.ganzithon.moyang.meeting.service;

import com.ganzithon.moyang.meeting.domain.Meeting;
import com.ganzithon.moyang.meeting.domain.MeetingEnums.MeetingStatus;
import com.ganzithon.moyang.meeting.domain.MeetingEnums.MeetingType;
import com.ganzithon.moyang.meeting.domain.MeetingParticipant;
import com.ganzithon.moyang.meeting.repository.MeetingParticipantRepository;
import org.springframework.stereotype.Component;

/**
 * "지금 방에 실제로 있는 사람"의 기준을 한 곳에 모은다.
 * 실시간 모임은 대기실에 들어온 사람이 곧 참여자라서 신청을 유지한 사람 전부가 기준이다.
 * 예약 모임은 신청만 하고 입장하지 않은 사람이 있을 수 있으므로, 모임이 시작(진행 중)된 뒤와 종료된 뒤에는
 * <b>입장한 사람만</b> 채팅 · 종료 동의 · 단계 진행 · 집계 · 회고 카드의 대상이다.
 * 시작 전(대기)이나 취소된 모임에서는 신청한 사람 모두 상태를 읽을 수 있다(시작·취소를 폴링으로 알아채야 하므로).
 */
@Component
public class MeetingRoster {

    private final MeetingParticipantRepository participants;

    public MeetingRoster(MeetingParticipantRepository participants) {
        this.participants = participants;
    }

    /** 이 사람이 방 안(채팅·종료·단계)에 접근할 수 있는지. 신청을 유지한 사람인지는 부르는 쪽이 먼저 확인한다. */
    public boolean hasRoomAccess(Meeting meeting, MeetingParticipant participant) {
        return !roomRequiresEntry(meeting) || participant.hasEntered();
    }

    /** 방의 인원 수. 집계(전원 종료 동의 · 그림 제출 · 폴링의 participantCount)의 분모가 된다. */
    public long countInRoom(Meeting meeting) {
        return roomRequiresEntry(meeting)
                ? participants.countByMeetingIdAndCanceledAtIsNullAndEnteredAtIsNotNull(meeting.getId())
                : participants.countActiveByMeetingId(meeting.getId());
    }

    /** 입장 여부를 따지는 때: 예약 모임이 진행 중이거나 종료된 뒤. */
    public static boolean roomRequiresEntry(Meeting meeting) {
        return meeting.getType() == MeetingType.SCHEDULED
                && (meeting.getStatus() == MeetingStatus.IN_PROGRESS || meeting.getStatus() == MeetingStatus.ENDED);
    }
}
