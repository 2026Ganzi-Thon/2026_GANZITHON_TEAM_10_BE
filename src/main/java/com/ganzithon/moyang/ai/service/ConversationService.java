package com.ganzithon.moyang.ai.service;

import com.ganzithon.moyang.ai.dto.ConversationDtos;

import com.ganzithon.moyang.ai.domain.Conversation;
import com.ganzithon.moyang.ai.domain.ConversationMessage;
import com.ganzithon.moyang.ai.repository.ConversationRepository;
import com.ganzithon.moyang.ai.repository.ConversationMessageRepository;
import com.ganzithon.moyang.ai.repository.MeetingCardRepository;
import com.ganzithon.moyang.ai.dto.ConversationDtos.RecordView;
import java.util.Optional;

import com.ganzithon.moyang.common.port.Ports.RecordPort;
import com.ganzithon.moyang.ai.service.ConversationAi;
import com.ganzithon.moyang.ai.service.ConversationAi.Card;
import com.ganzithon.moyang.ai.service.ConversationAi.Context;
import com.ganzithon.moyang.ai.service.ConversationAi.Line;
import com.ganzithon.moyang.ai.service.ConversationAi.Question;
import com.ganzithon.moyang.common.exception.BusinessException;
import com.ganzithon.moyang.common.exception.ErrorCode;
import com.ganzithon.moyang.ai.dto.ConversationDtos.CardView;
import com.ganzithon.moyang.ai.dto.ConversationDtos.AnalysisView;
import com.ganzithon.moyang.ai.dto.ConversationDtos.DetailResponse;
import com.ganzithon.moyang.ai.dto.ConversationDtos.DrawingView;
import com.ganzithon.moyang.ai.dto.ConversationDtos.EndResponse;
import com.ganzithon.moyang.ai.dto.ConversationDtos.InProgressItem;
import com.ganzithon.moyang.ai.dto.ConversationDtos.InProgressResponse;
import com.ganzithon.moyang.ai.dto.ConversationDtos.MessageRequest;
import com.ganzithon.moyang.ai.dto.ConversationDtos.MessageView;
import com.ganzithon.moyang.ai.dto.ConversationDtos.QuestionView;
import com.ganzithon.moyang.ai.dto.ConversationDtos.ReplyResponse;
import com.ganzithon.moyang.ai.dto.ConversationDtos.StartResponse;
import com.ganzithon.moyang.ai.dto.ConversationDtos.SupportNotice;
import com.ganzithon.moyang.ai.domain.ConversationMessage.Type;
import com.ganzithon.moyang.drawing.domain.Drawing;
import com.ganzithon.moyang.drawing.repository.DrawingRepository;
import com.ganzithon.moyang.common.port.Ports.TopicInfo;
import com.ganzithon.moyang.common.port.Ports.TopicReader;
import com.ganzithon.moyang.common.port.Ports.UserContextProvider;
import com.ganzithon.moyang.common.port.Ports.WoolPort;
import java.util.ArrayList;
import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * AI 그림 대화. AI 호출은 최대 30초가 걸릴 수 있어 DB 트랜잭션 밖에서 하고,
 * 결과 저장만 짧은 트랜잭션으로 묶는다 (DB 커넥션을 오래 잡지 않기 위해).
 */
@Service
public class ConversationService {
    private static final int MAX_CONTENT = 1000;
    private static final int EXTEND_COST = 1;

    private final ConversationRepository conversations;
    private final ConversationMessageRepository messages;
    private final DrawingRepository drawings;
    private final RecordPort records;
    private final ConversationAi ai;
    private final UserContextProvider users;
    private final TopicReader topics;
    private final WoolPort wool;
    private final TransactionTemplate tx;
    private final MeetingCardRepository meetingCards;

    public ConversationService(ConversationRepository conversations, ConversationMessageRepository messages,
                               DrawingRepository drawings, RecordPort records, ConversationAi ai,
                               UserContextProvider users, TopicReader topics, WoolPort wool,
                               TransactionTemplate tx, MeetingCardRepository meetingCards) {
        this.meetingCards = meetingCards;
        this.conversations = conversations;
        this.messages = messages;
        this.drawings = drawings;
        this.records = records;
        this.ai = ai;
        this.users = users;
        this.topics = topics;
        this.wool = wool;
        this.tx = tx;
    }

    public StartResponse start(long userId, Long drawingId) {
        if (drawingId == null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        Drawing drawing = drawings.findById(drawingId)
                .orElseThrow(() -> new BusinessException(ErrorCode.DRAWING_NOT_FOUND));
        if (!drawing.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        if (conversations.findByRecordId(drawing.getRecordId()).isPresent()) {
            throw new BusinessException(ErrorCode.CONVERSATION_ALREADY_EXISTS);
        }

        Question q = ai.nextQuestion(context(drawing), List.of(), 1);
        try {
            return tx.execute(status -> {
                Conversation c = conversations.save(new Conversation(drawing.getRecordId(), userId));
                c.asked(false);
                ConversationMessage m = messages.save(ConversationMessage.question(c.getId(), q.content(), 1,
                        q.fallback()));
                return new StartResponse(c.getId(), drawing.getId(), c.getRecordId(), c.getStatus().name(),
                        new QuestionView(m.getId(), m.getContent(), 1), c.getMaxQuestions(), q.fallback(), null);
            });
        } catch (DataIntegrityViolationException e) {
            // 동시에 두 번 시작한 경우: record_id UNIQUE 제약이 막는다
            throw new BusinessException(ErrorCode.CONVERSATION_ALREADY_EXISTS);
        }
    }

    public ReplyResponse reply(long userId, long conversationId, MessageRequest req) {
        if (req == null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        Conversation c = owned(userId, conversationId);
        if (c.isEnded()) {
            throw new BusinessException(ErrorCode.CONVERSATION_ALREADY_ENDED);
        }
        boolean useWool = Boolean.TRUE.equals(req.useWool());
        Type type = parseType(req.type());
        List<ConversationMessage> history = new ArrayList<>(messages.findByConversationIdOrderByIdAsc(c.getId()));
        boolean questionPending = !history.isEmpty() && history.get(history.size() - 1).isAi();

        // type 없이 useWool만: 답은 이미 보냈고 '양털 1개로 더 이야기하기'만 누른 경우
        if (type == null && (!useWool || questionPending)) {
            throw new BusinessException(ErrorCode.INVALID_MESSAGE_TYPE);
        }
        String content = null;
        if (type == Type.ANSWER) {
            content = req.content() == null ? "" : req.content().strip();
            if (content.isEmpty()) {
                throw new BusinessException(ErrorCode.EMPTY_CONTENT);
            }
            if (content.length() > MAX_CONTENT) {
                throw new BusinessException(ErrorCode.CONTENT_TOO_LONG);
            }
        }

        boolean free = c.hasFreeQuestionLeft();
        boolean crisis = ai.hasCrisisKeyword(content);
        // 이미 답했고 남은 무료 질문도 없는데 또 답을 보내면 받지 않는다 (같은 답이 카드에 두 번 들어가지 않게)
        if (type != null && !questionPending && !free && !useWool) {
            throw new BusinessException(ErrorCode.INVALID_MESSAGE_TYPE);
        }
        // 양털 검증은 답을 저장하기 전에 끝낸다 (409인데 답만 저장되는 일이 없게)
        if (!crisis && !free && useWool) {
            if (!c.canAddExtraQuestion()) {
                throw new BusinessException(ErrorCode.EXTEND_LIMIT_REACHED);
            }
            if (wool.balance(userId) < EXTEND_COST) {
                throw new BusinessException(ErrorCode.NOT_ENOUGH_WOOL);
            }
        }

        ConversationMessage userMessage = type == null ? null
                : ConversationMessage.user(c.getId(), type, content, c.getQuestionCount(), crisis);
        if (crisis) {
            messages.save(userMessage);
            return noQuestion(c, SupportNotice.DEFAULT);
        }
        if (!free && !useWool) {
            messages.save(userMessage);
            return noQuestion(c, null);
        }
        if (userMessage != null) {
            history.add(userMessage);
        }

        int asked = c.getQuestionCount();
        int order = asked + 1;
        Drawing drawing = drawingOf(c);
        Question q = ai.nextQuestion(context(drawing), lines(history, false), order);
        // AI 2차 감지: 질문을 내보내지 않고 안내만 한다. 양털도 차감하지 않는다
        boolean support = q.needsSupport() && type == Type.ANSWER;
        if (support) {
            userMessage.markNeedsSupport();
        }

        boolean paid = !free;
        // 답과 다음 질문은 한 트랜잭션으로 저장한다 (실패하면 답만 남는 일이 없게)
        return tx.execute(status -> {
            Conversation fresh = conversations.findById(c.getId()).orElseThrow();
            if (fresh.isEnded()) {
                throw new BusinessException(ErrorCode.CONVERSATION_ALREADY_ENDED);
            }
            if (fresh.getQuestionCount() != asked) {
                // AI를 기다리는 사이 같은 대화의 다른 요청(연타 · 재시도)이 먼저 처리됨
                throw new BusinessException(ErrorCode.CONCURRENT_REQUEST);
            }
            if (userMessage != null) {
                messages.save(userMessage);
            }
            if (support) {
                return noQuestion(fresh, SupportNotice.DEFAULT);
            }
            fresh.asked(paid);
            if (paid) {
                // 같은 질문 번호로는 한 번만 차감된다 (referenceId 중복 방지는 Sheep 쪽 UNIQUE)
                wool.spend(userId, "conversation:" + fresh.getId() + ":question:" + order);
            }
            ConversationMessage m = messages.save(ConversationMessage.question(fresh.getId(), q.content(), order,
                    q.fallback()));
            return new ReplyResponse(fresh.getId(), new QuestionView(m.getId(), m.getContent(), order),
                    fresh.remainingFreeQuestions(), false, false, EXTEND_COST, paid ? EXTEND_COST : 0,
                    q.fallback(), null);
        });
    }

    public EndResponse end(long userId, long conversationId, String status) {
        if (!"ENDED".equals(status)) {
            throw new BusinessException(ErrorCode.INVALID_STATUS);
        }
        Conversation c = owned(userId, conversationId);
        if (c.isEnded()) {
            throw new BusinessException(ErrorCode.CONVERSATION_ALREADY_ENDED);
        }
        Drawing drawing = drawingOf(c);
        Context ctx = context(drawing);
        Card card = ai.card(ctx, lines(messages.findByConversationIdOrderByIdAsc(c.getId()), true));

        return tx.execute(s -> {
            Conversation fresh = conversations.findById(c.getId()).orElseThrow();
            if (fresh.isEnded()) {
                throw new BusinessException(ErrorCode.CONVERSATION_ALREADY_ENDED);
            }
            fresh.end(card.whatISaid(), card.thoughts(), card.todaysStory(), card.fallback());
            if (card.analysis() != null) {
                var a = card.analysis();
                fresh.analyzed(a.mainElements(), a.spatialComposition(), a.lineExpression(), a.emotionMeaning());
            }
            records.cardReady(fresh.getRecordId());
            return new EndResponse(fresh.getId(), fresh.getRecordId(), fresh.getStatus().name(), drawingView(drawing, ctx),
                    cardView(fresh), analysisView(fresh), fresh.isCardFallback());
        });
    }

    public DetailResponse detail(long userId, long conversationId) {
        Conversation c = owned(userId, conversationId);
        Drawing drawing = drawingOf(c);
        List<ConversationMessage> history = messages.findByConversationIdOrderByIdAsc(c.getId());
        boolean questionPending = !history.isEmpty() && history.get(history.size() - 1).isAi();
        boolean canExtend = !c.isEnded() && !questionPending && !c.hasFreeQuestionLeft() && c.canAddExtraQuestion();
        return new DetailResponse(c.getId(), drawing.getId(), c.getRecordId(), c.getStatus().name(),
                messageViews(history), c.remainingFreeQuestions(), canExtend, drawingView(drawing, context(drawing)),
                c.isEnded() ? cardView(c) : null, analysisView(c), c.isCardFallback());
    }

    /**
     * 기록 상세(record 담당 박준석)용: recordId로 그림 · 회고 카드 · 대화 내용을 한 번에 돌려준다.
     * 카드는 개인 대화가 끝났으면 그 카드, 아니면 모임 회고 카드 (둘 다 없으면 null = 아직 준비 안 됨).
     * 대화 내용은 개인 대화만 (모임 채팅은 승효 담당이고 24시간 뒤 삭제). 소유자 검증은 부르는 쪽에서 한다.
     */
    public Optional<RecordView> recordView(long recordId) {
        return drawings.findByRecordId(recordId).map(drawing -> {
            Optional<Conversation> conv = conversations.findByRecordId(recordId);
            List<MessageView> views = conv.map(c -> messageViews(messages.findByConversationIdOrderByIdAsc(c.getId())))
                    .orElse(List.of());
            TopicInfo topic = topicOf(drawing);
            DrawingView drawingView = new DrawingView(drawing.getId(), drawing.imageUrl(),
                    topic == null ? null : topic.title(), drawing.getNote());
            if (conv.isPresent() && conv.get().isEnded()) {
                Conversation c = conv.get();
                return new RecordView(recordId, drawingView, cardView(c), analysisView(c), c.isCardFallback(), views);
            }
            return meetingCards.findFirstByRecordIdOrderByIdDesc(recordId)
                    .map(m -> new RecordView(recordId, drawingView,
                            new CardView(m.getWhatISaid(), m.getThoughts(), m.getTodaysStory()), null, m.isFallback(), views))
                    .orElseGet(() -> new RecordView(recordId, drawingView, null, null, false, views));
        });
    }

    private static List<MessageView> messageViews(List<ConversationMessage> history) {
        return history.stream()
                .map(m -> new MessageView(m.getId(), m.getRole().name(), m.getType().name(), m.getContent(),
                        m.getQuestionOrder(), ConversationDtos.kst(m.getCreatedAt())))
                .toList();
    }

    public InProgressResponse inProgress(long userId, String status) {
        if (!"IN_PROGRESS".equals(status)) {
            throw new BusinessException(ErrorCode.INVALID_STATUS);
        }
        return conversations.findFirstByUserIdAndStatusOrderByIdDesc(userId, Conversation.Status.IN_PROGRESS)
                .map(c -> {
                    Drawing drawing = drawingOf(c);
                    return new InProgressResponse(new InProgressItem(c.getId(), drawing.getId(), c.getRecordId(),
                            drawing.imageUrl(), topicOf(drawing) == null ? null : topicOf(drawing).title(),
                            c.remainingFreeQuestions(), ConversationDtos.kst(c.getCreatedAt())));
                })
                .orElseGet(() -> new InProgressResponse(null));
    }

    private Conversation owned(long userId, long conversationId) {
        Conversation c = conversations.findById(conversationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CONVERSATION_NOT_FOUND));
        if (!c.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        return c;
    }

    private Drawing drawingOf(Conversation c) {
        return drawings.findByRecordId(c.getRecordId())
                .orElseThrow(() -> new BusinessException(ErrorCode.DRAWING_NOT_FOUND));
    }

    private static Type parseType(String raw) {
        if (raw == null) {
            return null;
        }
        return switch (raw) {
            case "ANSWER" -> Type.ANSWER;
            case "DONT_KNOW" -> Type.DONT_KNOW;
            case "SKIP" -> Type.SKIP;
            default -> throw new BusinessException(ErrorCode.INVALID_MESSAGE_TYPE);
        };
    }

    private ReplyResponse noQuestion(Conversation c, SupportNotice notice) {
        boolean finished = notice == null;
        return new ReplyResponse(c.getId(), null, c.remainingFreeQuestions(), finished,
                finished && c.canAddExtraQuestion(), EXTEND_COST, 0, false, notice);
    }

    private TopicInfo topicOf(Drawing drawing) {
        return drawing.getTopicId() == null ? null : topics.find(drawing.getTopicId()).orElse(null);
    }

    private Context context(Drawing drawing) {
        TopicInfo topic = topicOf(drawing);
        return new Context(users.get(drawing.getUserId()), topic == null ? null : topic.title(),
                topic == null ? null : topic.description(), drawing.getNote());
    }

    private DrawingView drawingView(Drawing drawing, Context ctx) {
        return new DrawingView(drawing.getId(), drawing.imageUrl(), ctx.topicTitle(), drawing.getNote());
    }

    private CardView cardView(Conversation c) {
        return new CardView(c.getCardWhatISaid(), c.getCardThoughts(), c.getCardTodaysStory());
    }

    /** 그림 자세히 살펴보기 4칸. 하나도 없으면(fallback 카드 · 진행 중) null. */
    private static AnalysisView analysisView(Conversation c) {
        if (c.getMainElements() == null && c.getSpatialComposition() == null && c.getLineExpression() == null
                && c.getEmotionMeaning() == null) {
            return null;
        }
        return new AnalysisView(c.getMainElements(), c.getSpatialComposition(), c.getLineExpression(),
                c.getEmotionMeaning());
    }

    /** forCard=true면 위기 표현이 담긴 답을 뺀다 (카드에 그대로 적히지 않게). */
    private List<Line> lines(List<ConversationMessage> history, boolean forCard) {
        List<Line> lines = new ArrayList<>();
        for (ConversationMessage m : history) {
            if (m.isAi()) {
                lines.add(new Line(true, m.getContent(), true));
            } else if (m.getType() == Type.ANSWER) {
                if (!(forCard && m.isNeedsSupport())) {
                    lines.add(new Line(false, m.getContent(), true));
                }
            } else {
                lines.add(new Line(false, m.getType() == Type.SKIP ? "(건너뛸게요)" : "(잘 모르겠어요)", false));
            }
        }
        return lines;
    }
}
