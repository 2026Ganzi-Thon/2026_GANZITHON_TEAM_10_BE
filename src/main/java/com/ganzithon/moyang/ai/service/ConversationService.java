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
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * AI 그림 대화. AI 호출은 최대 30초가 걸릴 수 있어 DB 트랜잭션 밖에서 하고,
 * 결과 저장만 짧은 트랜잭션으로 묶는다 (DB 커넥션을 오래 잡지 않기 위해).
 */
@Service
public class ConversationService {
    private static final Logger log = LoggerFactory.getLogger(ConversationService.class);
    private static final int MAX_CONTENT = 1000;
    private static final int EXTEND_COST = 1;
    private static final int END_ATTEMPTS = 2;

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
        if (userMessage != null) {
            history.add(userMessage);
        }

        // 위기 키워드면 AI 없이 바로 안내한다
        boolean askNext = !crisis && (free || useWool);
        int asked = c.getQuestionCount();
        int order = asked + 1;
        // 다음 질문이 없는 마지막 답도 AI 2차 감지를 거친다 (이때 AI가 만든 질문은 버린다). AI 실패면 키워드 판정만
        Question q = askNext || (!crisis && type == Type.ANSWER)
                ? ai.nextQuestion(context(drawingOf(c)), lines(history, false), order) : null;
        if (q != null && q.fallback() && type == Type.ANSWER) {
            // AI 2차 감지를 못 해 키워드 판정만 적용됨. 위기 기능 저하를 알 수 있게 남긴다 (대화 내용은 남기지 않는다)
            log.warn("crisis AI check unavailable, keyword only: conversationId={}, question={}", c.getId(), asked);
        }
        // AI 2차 감지: 질문을 내보내지 않고 안내만 한다. 양털도 차감하지 않는다 (키워드 위기는 만들 때 이미 표시됨)
        boolean aiSupport = q != null && q.needsSupport() && type == Type.ANSWER;
        if (aiSupport) {
            userMessage.markNeedsSupport();
        }
        boolean support = crisis || aiSupport;

        boolean paid = !free;
        // 모든 저장은 한 트랜잭션에서 한다 (실패하면 답만 남는 일이 없게, 연타로 같은 답이 두 번 저장되지 않게)
        return tx.execute(status -> {
            Conversation fresh = conversations.findById(c.getId()).orElseThrow();
            if (fresh.isEnded()) {
                throw new BusinessException(ErrorCode.CONVERSATION_ALREADY_ENDED);
            }
            // 이 요청이 대화를 읽은 뒤 다른 요청(연타 · 재시도)이 먼저 저장했으면 409. 모든 저장이 version을 올리므로
            // (touch · asked · end) 질문 수 · 마지막 메시지 중 무엇이 어긋나도 여기서 잡힌다
            if (fresh.getVersion() != c.getVersion()) {
                throw new BusinessException(ErrorCode.CONCURRENT_REQUEST);
            }
            fresh.touch(); // 지금 동시에 겹친 다른 저장과는 커밋 때 @Version 충돌 → 409
            if (userMessage != null) {
                messages.save(userMessage);
            }
            if (support) {
                return noQuestion(fresh, SupportNotice.DEFAULT);
            }
            if (!askNext) {
                return noQuestion(fresh, null);
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
        // 카드를 만드는 사이(최대 30초) 답이 저장되면 그 답이 빠진 카드가 된다 → 그 답까지 넣어 카드를 다시 만든다
        for (int attempt = 1; ; attempt++) {
            List<ConversationMessage> history = messages.findByConversationIdOrderByIdAsc(c.getId());
            Long lastSeenId = lastId(history);
            Card card = ai.card(ctx, lines(history, true));
            String retryReason;
            try {
                EndResponse ended = tx.execute(s -> {
                    Conversation fresh = conversations.findById(c.getId()).orElseThrow();
                    if (fresh.isEnded()) {
                        throw new BusinessException(ErrorCode.CONVERSATION_ALREADY_ENDED);
                    }
                    if (hasNewMessage(fresh.getId(), lastSeenId)) {
                        return null;
                    }
                    fresh.end(card.whatISaid(), card.thoughts(), card.todaysStory(), card.fallback());
                    if (card.analysis() != null) {
                        var a = card.analysis();
                        fresh.analyzed(a.mainElements(), a.spatialComposition(), a.lineExpression(),
                                a.emotionMeaning());
                    }
                    records.cardReady(fresh.getRecordId());
                    return new EndResponse(fresh.getId(), fresh.getRecordId(), fresh.getStatus().name(),
                            drawingView(drawing, ctx), cardView(fresh), analysisView(fresh), fresh.isCardFallback());
                });
                if (ended != null) {
                    return ended;
                }
                retryReason = "new-message";
            } catch (OptimisticLockingFailureException e) {
                // 답 저장 · 다른 마치기와 같은 순간에 겹침: 다음 시도에서 다시 읽는다
                retryReason = "optimistic-lock";
            }
            // 늦게 온 답 하나는 두 번째 시도에 담긴다. 마치는 동안 계속 답이 들어올 때만 409
            if (attempt == END_ATTEMPTS) {
                log.warn("end gave up: conversationId={}, attempts={}, reason={}", c.getId(), attempt, retryReason);
                throw new BusinessException(ErrorCode.CONCURRENT_REQUEST);
            }
            log.info("end retry: conversationId={}, attempt={}, reason={}", c.getId(), attempt, retryReason);
            // 이중 탭으로 다른 마치기가 먼저 끝냈으면 카드를 또 만들지 않는다
            if (conversations.findById(c.getId()).map(Conversation::isEnded).orElse(false)) {
                throw new BusinessException(ErrorCode.CONVERSATION_ALREADY_ENDED);
            }
        }
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

    private static Long lastId(List<ConversationMessage> history) {
        return history.isEmpty() ? null : history.get(history.size() - 1).getId();
    }

    /** 요청을 시작할 때 본 마지막 메시지 뒤로 새 메시지가 있는지 = AI를 기다리는 사이 다른 요청(연타 · 재시도)이 먼저 저장했는지. */
    private boolean hasNewMessage(long conversationId, Long lastSeenId) {
        Long now = messages.findFirstByConversationIdOrderByIdDesc(conversationId)
                .map(ConversationMessage::getId).orElse(null);
        return !Objects.equals(now, lastSeenId);
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
