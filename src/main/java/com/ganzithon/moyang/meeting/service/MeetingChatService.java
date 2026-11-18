package com.ganzithon.moyang.meeting.service;

import com.ganzithon.moyang.common.exception.BusinessException;
import com.ganzithon.moyang.common.exception.ErrorCode;
import com.ganzithon.moyang.drawing.domain.Drawing;
import com.ganzithon.moyang.drawing.repository.DrawingRepository;
import com.ganzithon.moyang.meeting.domain.Meeting;
import com.ganzithon.moyang.meeting.domain.MeetingEnums.MeetingPhase;
import com.ganzithon.moyang.meeting.domain.MeetingEnums.MeetingStatus;
import com.ganzithon.moyang.meeting.domain.MeetingEnums.MeetingType;
import com.ganzithon.moyang.meeting.domain.MeetingEnums.MessageType;
import com.ganzithon.moyang.meeting.domain.MeetingMessage;
import com.ganzithon.moyang.meeting.domain.MeetingParticipant;
import com.ganzithon.moyang.meeting.dto.MeetingDtos.ChatMessageItem;
import com.ganzithon.moyang.meeting.dto.MeetingDtos.ChatPollResponse;
import com.ganzithon.moyang.meeting.dto.MeetingDtos.SendMessageRequest;
import com.ganzithon.moyang.meeting.dto.MeetingDtos.SendMessageResponse;
import com.ganzithon.moyang.meeting.repository.MeetingMessageRepository;
import com.ganzithon.moyang.meeting.repository.MeetingParticipantRepository;
import com.ganzithon.moyang.meeting.repository.MeetingRepository;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 모임 채팅 보내기 · 폴링. 보내기는 모임 행 락 안에서 저장해서(락 순서: 모임 행 → 그 밖의 것) 메시지 id 순서와 커밋 순서가
 * 같다. 그래야 afterId 폴링이 늦게 커밋된 메시지를 놓치지 않는다. 모임 채팅에서는 위기 감지를 하지 않는다(회의 결정).
 */
@Service
public class MeetingChatService {

    private final MeetingRepository meetings;
    private final MeetingParticipantRepository participants;
    private final MeetingMessageRepository messages;
    private final DrawingRepository drawings;
    private final MeetingQueryService time;
    private final MeetingPhaseService phases;

    public MeetingChatService(MeetingRepository meetings, MeetingParticipantRepository participants,
                              MeetingMessageRepository messages, DrawingRepository drawings,
                              MeetingQueryService time, MeetingPhaseService phases) {
        this.meetings = meetings;
        this.participants = participants;
        this.messages = messages;
        this.drawings = drawings;
        this.time = time;
        this.phases = phases;
    }

    /**
     * 검사 순서: 형식(type · 내용 · 길이 · drawingId)은 400 → 없는 모임 404 → 참여자 아님 403 → 진행 중 아님 409
     * → 내 그림이 아님 403. 참여 중이고 진행 중인 모임에서만 보낼 수 있다.
     */
    @Transactional
    public SendMessageResponse send(long userId, long meetingId, SendMessageRequest req) {
        MessageType type = parseType(req.type());
        String content = blankToNull(req.content());
        if (type == MessageType.TEXT && content == null) {
            throw new BusinessException(ErrorCode.EMPTY_CONTENT, "메시지 내용을 입력해 주세요.");
        }
        if (content != null && content.codePointCount(0, content.length()) > MeetingMessage.MAX_CONTENT) {
            throw new BusinessException(ErrorCode.CONTENT_TOO_LONG,
                    "메시지는 " + MeetingMessage.MAX_CONTENT + "자까지 쓸 수 있어요.");
        }
        if (type == MessageType.DRAWING && req.drawingId() == null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }

        LocalDateTime now = time.now();
        Meeting meeting = meetings.findForUpdate(meetingId).filter(m -> !m.expired(now))
                .orElseThrow(() -> new BusinessException(ErrorCode.MEETING_NOT_FOUND));
        requireActiveParticipant(meetingId, userId);
        if (meeting.getStatus() != MeetingStatus.IN_PROGRESS) {
            throw new BusinessException(ErrorCode.MEETING_NOT_IN_PROGRESS);
        }

        if (type == MessageType.DRAWING && meeting.getPhase() == MeetingPhase.INTRO) {
            throw new BusinessException(ErrorCode.DRAWING_NOT_OPEN);
        }

        MeetingMessage message;
        if (type == MessageType.DRAWING) {
            Drawing drawing = drawings.findById(req.drawingId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.DRAWING_NOT_FOUND));
            if (!Long.valueOf(userId).equals(drawing.getUserId())) {
                throw new BusinessException(ErrorCode.FORBIDDEN);
            }
            message = MeetingMessage.drawing(meetingId, userId, drawing.getId(), content, now);
        } else {
            message = MeetingMessage.text(meetingId, userId, content, now);
        }
        MeetingMessage saved = messages.saveAndFlush(message);
        // 그림 단계에서 참여자 모두가 그림을 올렸으면 서버가 자동으로 그림 이야기(SHARING) 단계로 넘긴다
        if (type == MessageType.DRAWING && meeting.getPhase() == MeetingPhase.DRAWING
                && messages.countDrawingSubmitters(meetingId) >= participants.countActiveByMeetingId(meetingId)) {
            phases.advance(meeting, now);
        }
        return new SendMessageResponse(saved.getId(), MeetingQueryService.toOffset(saved.getCreatedAt()));
    }

    /**
     * 채팅 폴링. 시작 전(대기실)·취소·종료 상태에서도 참여자는 읽을 수 있다(프론트가 status 변화를 폴링으로 알아채야 하므로).
     * 종료 후 24시간이 지난 방은 404.
     */
    @Transactional(readOnly = true)
    public ChatPollResponse poll(long userId, long meetingId, Long afterId) {
        LocalDateTime now = time.now();
        Meeting meeting = meetings.findById(meetingId).filter(m -> !m.expired(now))
                .orElseThrow(() -> new BusinessException(ErrorCode.MEETING_NOT_FOUND));
        MeetingParticipant me = requireActiveParticipant(meetingId, userId);

        List<MeetingMessage> rows = afterId == null
                ? messages.findByMeetingIdOrderByIdAsc(meetingId)
                : messages.findByMeetingIdAndIdGreaterThanOrderByIdAsc(meetingId, Math.max(afterId, 0));

        List<MeetingParticipant> active = participants.findByMeetingIdAndCanceledAtIsNull(meetingId);
        Map<Long, String> nicknames = new HashMap<>();
        active.forEach(p -> nicknames.put(p.getUserId(), p.getMeetingNickname()));

        List<ChatMessageItem> items = rows.stream().map(r -> toItem(r, userId, nicknames)).toList();

        MeetingStatus status = meeting.statusAt(now);
        boolean scheduled = meeting.getType() == MeetingType.SCHEDULED;
        // 예약 모임의 주제는 시작(진행 중)된 뒤에 공개한다(상세 조회와 같은 규칙)
        boolean topicVisible = !scheduled || status == MeetingStatus.IN_PROGRESS || status == MeetingStatus.ENDED;
        return new ChatPollResponse(status.name(),
                meeting.getPhase() == null ? null : meeting.getPhase().name(),
                meeting.getPhase() == null ? null : meeting.getPhase().ordinal() + 1,
                topicVisible ? meeting.getTopicTitle() : null,
                (int) messages.countDrawingSubmitters(meetingId), active.size(),
                (int) active.stream().filter(MeetingParticipant::hasAgreedEnd).count(), me.hasAgreedEnd(),
                scheduled ? MeetingQueryService.toOffset(meeting.getStartAt().plus(Meeting.SCHEDULED_LENGTH)) : null,
                status == MeetingStatus.ENDED && meeting.getEndedAt() != null
                        ? MeetingQueryService.toOffset(meeting.getEndedAt().plus(Meeting.ENDED_VISIBLE)) : null,
                items);
    }

    private ChatMessageItem toItem(MeetingMessage r, long viewerId, Map<Long, String> nicknames) {
        boolean system = r.getType() == MessageType.SYSTEM;
        Long sender = r.getUserId();
        return new ChatMessageItem(r.getId(), r.getType().name(),
                system || sender == null ? null : nicknames.get(sender),
                r.getContent(),
                r.getDrawingId() == null ? null : "/api/drawings/" + r.getDrawingId() + "/image",
                sender != null && sender == viewerId,
                MeetingQueryService.toOffset(r.getCreatedAt()));
    }

    private MeetingParticipant requireActiveParticipant(long meetingId, long userId) {
        return participants.findByMeetingIdAndUserId(meetingId, userId).filter(MeetingParticipant::isActive)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_PARTICIPANT));
    }

    /** 참여자가 보낼 수 있는 종류는 TEXT · DRAWING뿐이다(SYSTEM은 서버만). 기존 코드의 문구는 AI 대화용이라 이 API의 문구를 따로 준다. */
    private static MessageType parseType(String raw) {
        if ("TEXT".equals(raw)) {
            return MessageType.TEXT;
        }
        if ("DRAWING".equals(raw)) {
            return MessageType.DRAWING;
        }
        throw new BusinessException(ErrorCode.INVALID_MESSAGE_TYPE, "type은 TEXT, DRAWING 중 하나여야 해요.");
    }

    private static String blankToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.strip();
        return t.isEmpty() ? null : t;
    }
}
