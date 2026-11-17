package com.ganzithon.moyang.ai.service;

import com.ganzithon.moyang.ai.domain.MeetingCard;
import com.ganzithon.moyang.ai.repository.MeetingCardRepository;

import com.ganzithon.moyang.common.port.Ports.RecordPort;
import com.ganzithon.moyang.ai.service.ConversationAi.Card;
import com.ganzithon.moyang.ai.service.ConversationAi.Context;
import com.ganzithon.moyang.ai.service.ConversationAi.Line;
import com.ganzithon.moyang.common.exception.BusinessException;
import com.ganzithon.moyang.common.exception.ErrorCode;
import com.ganzithon.moyang.ai.dto.ConversationDtos.CardView;
import com.ganzithon.moyang.ai.dto.ConversationDtos.DrawingView;
import com.ganzithon.moyang.ai.dto.ConversationDtos.MeetingCardResponse;
import com.ganzithon.moyang.drawing.domain.Drawing;
import com.ganzithon.moyang.drawing.repository.DrawingRepository;
import com.ganzithon.moyang.common.port.Ports.MeetingInfo;
import com.ganzithon.moyang.common.port.Ports.MeetingMember;
import com.ganzithon.moyang.common.port.Ports.MeetingReader;
import com.ganzithon.moyang.common.port.Ports.TopicReader;
import com.ganzithon.moyang.common.port.Ports.UserContext;
import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 모임 회고 카드. 모임에서 AI는 대화에 끼어들지 않고, 끝난 뒤 참여자별 카드만 만든다.
 * 재료는 본인이 쓴 메시지 + 본인 그림 설명뿐이고, 다른 참여자 발언과 온보딩 정보는 넣지 않는다.
 */
@Service
public class MeetingCardService {


    private final MeetingCardRepository cards;
    private final MeetingReader meetings;
    private final DrawingRepository drawings;
    private final RecordPort records;
    private final TopicReader topics;
    private final ConversationAi ai;
    private final TransactionTemplate tx;

    public MeetingCardService(MeetingCardRepository cards, MeetingReader meetings, DrawingRepository drawings,
                              RecordPort records, TopicReader topics, ConversationAi ai,
                              TransactionTemplate tx) {
        this.cards = cards;
        this.meetings = meetings;
        this.drawings = drawings;
        this.records = records;
        this.topics = topics;
        this.ai = ai;
        this.tx = tx;
    }

    /**
     * 모임이 '전원 대화 종료'되는 순간 Meeting 서비스가 한 번 호출한다. 여러 번 불려도 카드는 한 장만 생긴다.
     * ponytail: 참여자별 AI 호출이 순차(최대 6명 × AI 응답 시간). 종료 요청이 느리면 Meeting 쪽에서 @Async로 부르거나 여기서 병렬화.
     */
    public void generateMeetingCards(long meetingId) {
        MeetingInfo info = meetings.find(meetingId).orElseThrow(() -> new BusinessException(ErrorCode.MEETING_NOT_FOUND));
        for (MeetingMember member : info.members()) {
            if (member.drawingId() == null || cards.existsByMeetingIdAndUserId(meetingId, member.userId())) {
                continue;
            }
            Drawing drawing = drawings.findById(member.drawingId()).orElse(null);
            if (drawing == null || !drawing.getUserId().equals(member.userId())) {
                continue; // 본인 그림이 아니면 카드를 만들지 않는다
            }
            // 모임에서는 위기 안내를 띄우지 않지만, 위기 표현이 담긴 메시지는 카드에 그대로 적히지 않게 뺀다
            List<Line> lines = member.messages().stream()
                    .filter(text -> text != null && !text.isBlank() && !ai.hasCrisisKeyword(text))
                    .map(text -> new Line(false, text, true))
                    .toList();
            Card card = ai.card(new Context(UserContext.EMPTY, topicTitle(drawing), null, drawing.getNote()), lines);
            try {
                tx.executeWithoutResult(status -> {
                    cards.save(new MeetingCard(meetingId, member.userId(), drawing.getRecordId(), drawing.getId(),
                            card.whatISaid(), card.thoughts(), card.todaysStory(), card.fallback()));
                    records.cardReady(drawing.getRecordId());
                });
            } catch (DataIntegrityViolationException e) {
                // 동시에 두 번 호출된 경우: (meeting_id, user_id) UNIQUE가 막는다
            }
        }
    }

    public MeetingCardResponse get(long userId, long meetingId) {
        MeetingInfo info = meetings.find(meetingId).orElseThrow(() -> new BusinessException(ErrorCode.MEETING_NOT_FOUND));
        if (info.members().stream().noneMatch(m -> m.userId() == userId)) {
            throw new BusinessException(ErrorCode.NOT_PARTICIPANT);
        }
        if (!info.ended()) {
            throw new BusinessException(ErrorCode.MEETING_NOT_ENDED);
        }
        MeetingCard card = cards.findByMeetingIdAndUserId(meetingId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NO_DRAWING_IN_MEETING));
        Drawing drawing = drawings.findById(card.getDrawingId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NO_DRAWING_IN_MEETING));
        return new MeetingCardResponse(meetingId, card.getRecordId(),
                new DrawingView(drawing.getId(), drawing.imageUrl(), topicTitle(drawing), drawing.getNote()),
                new CardView(card.getWhatISaid(), card.getThoughts(), card.getTodaysStory()), card.isFallback());
    }

    private String topicTitle(Drawing drawing) {
        return drawing.getTopicId() == null ? null
                : topics.find(drawing.getTopicId()).map(t -> t.title()).orElse(null);
    }
}
