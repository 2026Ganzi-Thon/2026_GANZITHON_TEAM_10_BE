package com.ganzithon.moyang.meeting.service;

import com.ganzithon.moyang.common.port.Ports.MeetingInfo;
import com.ganzithon.moyang.common.port.Ports.MeetingMember;
import com.ganzithon.moyang.common.port.Ports.MeetingReader;
import com.ganzithon.moyang.common.port.Ports.MeetingSharePort;
import com.ganzithon.moyang.meeting.domain.Meeting;
import com.ganzithon.moyang.meeting.domain.MeetingEnums.MeetingStatus;
import com.ganzithon.moyang.meeting.domain.MeetingEnums.MeetingType;
import com.ganzithon.moyang.meeting.domain.MeetingEnums.MessageType;
import com.ganzithon.moyang.meeting.domain.MeetingMessage;
import com.ganzithon.moyang.meeting.repository.MeetingMessageRepository;
import com.ganzithon.moyang.meeting.repository.MeetingParticipantRepository;
import com.ganzithon.moyang.meeting.repository.MeetingRepository;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 모임이 다른 도메인에 내주는 포트의 진짜 구현.
 * - MeetingSharePort: 모임 채팅에 공유된 그림을 그 모임 참여자가 볼 수 있게 한다(그림 이미지 조회 권한).
 * - MeetingReader: 모임 회고 카드를 만들 재료(종료 여부 · 참여자별 공유 그림 · 본인 텍스트 메시지).
 */
@Component
@Transactional(readOnly = true)
public class MeetingPortService implements MeetingSharePort, MeetingReader {

    private final MeetingRepository meetings;
    private final MeetingParticipantRepository participants;
    private final MeetingMessageRepository messages;
    private final MeetingQueryService time;

    public MeetingPortService(MeetingRepository meetings, MeetingParticipantRepository participants,
                              MeetingMessageRepository messages, MeetingQueryService time) {
        this.meetings = meetings;
        this.participants = participants;
        this.messages = messages;
        this.time = time;
    }

    /** 참여 중인 모임 채팅에 공유된 그림이면 true. 모임이 끝나고 24시간이 지나 채팅이 삭제된 뒤에는 false. */
    @Override
    public boolean isSharedWith(long drawingId, long userId) {
        return messages.countSharedWith(drawingId, userId, MessageType.DRAWING,
                time.now().minus(Meeting.ENDED_VISIBLE), MeetingType.LIVE) > 0;
    }

    /** 종료는 전원 대화 종료(ENDED)일 때만 true. 참여자는 신청을 유지한 사람이고, 그림은 가장 최근에 공유한 한 장이다. */
    @Override
    public Optional<MeetingInfo> find(long meetingId) {
        return meetings.findById(meetingId).map(meeting -> {
            List<MeetingMessage> all = messages.findByMeetingIdOrderByIdAsc(meetingId);
            Map<Long, Long> lastDrawing = new HashMap<>();
            Map<Long, List<String>> texts = new HashMap<>();
            for (MeetingMessage m : all) {
                if (m.getUserId() == null) {
                    continue;
                }
                if (m.getType() == MessageType.DRAWING && m.getDrawingId() != null) {
                    lastDrawing.put(m.getUserId(), m.getDrawingId());
                } else if (m.getType() == MessageType.TEXT && m.getContent() != null) {
                    texts.computeIfAbsent(m.getUserId(), k -> new ArrayList<>()).add(m.getContent());
                }
            }
            // 회고 카드는 실제로 방에 있던 사람에게만 만든다(예약 모임은 입장한 사람, 실시간은 신청을 유지한 사람)
            List<MeetingMember> members = participants.findByMeetingIdAndCanceledAtIsNull(meetingId).stream()
                    .filter(p -> meeting.getType() != MeetingType.SCHEDULED || p.hasEntered())
                    .map(p -> new MeetingMember(p.getUserId(), lastDrawing.get(p.getUserId()),
                            texts.getOrDefault(p.getUserId(), List.of())))
                    .toList();
            return new MeetingInfo(meeting.getStatus() == MeetingStatus.ENDED, members);
        });
    }
}
