package com.ganzithon.moyang.meeting.service;

import com.ganzithon.moyang.ai.service.MeetingCardService;
import jakarta.annotation.PreDestroy;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 모임이 종료된 뒤 회고 카드(AI 호출)를 만든다. 종료가 DB에 커밋된 <b>다음에</b>, 별도 스레드에서 딱 한 번만 부른다.
 * 카드 생성이 느리거나 실패해도 종료는 되돌아가지 않고 종료 응답도 기다리지 않는다. 실패하면 로그만 남긴다
 * (카드 서비스는 같은 모임을 다시 불러도 카드가 한 장만 생기므로, 필요하면 나중에 수동으로 다시 불러도 안전하다).
 * 테스트에서는 {@code moyang.meeting.card-async=false}로 같은 스레드에서 바로 실행한다.
 */
@Component
public class MeetingCardTrigger {

    private static final Logger log = LoggerFactory.getLogger(MeetingCardTrigger.class);

    private final MeetingCardService cards;
    private final Executor executor;
    private final ExecutorService owned; // 직접 만든 스레드 풀(같은 스레드 모드에서는 null)

    public MeetingCardTrigger(MeetingCardService cards,
                              @Value("${moyang.meeting.card-async:true}") boolean async) {
        this.cards = cards;
        if (async) {
            this.owned = Executors.newFixedThreadPool(2, runnable -> {
                Thread t = new Thread(runnable, "meeting-card");
                t.setDaemon(true);
                return t;
            });
            this.executor = owned;
        } else {
            this.owned = null;
            this.executor = Runnable::run;
        }
    }

    /** 종료가 커밋된 직후에 부른다(트랜잭션 밖에서). 어떤 경우에도 예외를 밖으로 던지지 않는다. */
    public void generateAfterEnd(long meetingId) {
        try {
            executor.execute(() -> generate(meetingId));
        } catch (RuntimeException e) {
            log.warn("모임 {} 회고 카드 생성을 시작하지 못했어요.", meetingId, e);
        }
    }

    private void generate(long meetingId) {
        try {
            cards.generateMeetingCards(meetingId);
        } catch (RuntimeException e) {
            log.warn("모임 {} 회고 카드 생성에 실패했어요(종료는 그대로 유지돼요).", meetingId, e);
        }
    }

    @PreDestroy
    void shutdown() {
        if (owned != null) {
            owned.shutdown();
        }
    }
}
