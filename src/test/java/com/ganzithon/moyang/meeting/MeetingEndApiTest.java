package com.ganzithon.moyang.meeting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ganzithon.moyang.ai.repository.MeetingCardRepository;
import com.ganzithon.moyang.ai.service.MeetingCardService;
import com.ganzithon.moyang.auth.JwtTokenProvider;
import com.ganzithon.moyang.common.port.Ports.RecordPort;
import com.ganzithon.moyang.drawing.domain.Drawing;
import com.ganzithon.moyang.drawing.repository.DrawingRepository;
import com.ganzithon.moyang.meeting.domain.Meeting;
import com.ganzithon.moyang.meeting.domain.MeetingEnums.MeetingStatus;
import com.ganzithon.moyang.meeting.domain.MeetingEnums.MessageType;
import com.ganzithon.moyang.meeting.domain.MeetingMessage;
import com.ganzithon.moyang.meeting.repository.MeetingMessageRepository;
import com.ganzithon.moyang.meeting.repository.MeetingRepository;
import com.ganzithon.moyang.sheep.service.SheepService;
import com.ganzithon.moyang.user.domain.User;
import com.ganzithon.moyang.user.repository.UserRepository;
import com.jayway.jsonpath.JsonPath;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.WebApplicationContext;

/**
 * 대화 종료 동의: 전원이 동의하면 종료(ENDED) → 커밋 직후 별도 스레드에서 회고 카드 생성(테스트에서는 같은 스레드).
 * 카드 생성이 실패해도 종료는 되돌아가지 않는다.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:meeting-end;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "moyang.upload-dir=build/test-uploads",
        "moyang.sheep.demo-daily-limit-off=true"
})
class MeetingEndApiTest {

    static final AtomicInteger SEQ = new AtomicInteger();
    static final AtomicLong RECORD = new AtomicLong(950_000);
    static final ZoneId KST = ZoneId.of("Asia/Seoul");

    @Autowired WebApplicationContext context;
    @Autowired JwtTokenProvider tokens;
    @Autowired UserRepository users;
    @Autowired MeetingRepository meetings;
    @Autowired MeetingMessageRepository messages;
    @Autowired DrawingRepository drawings;
    @Autowired MeetingCardRepository cardRepository;
    @Autowired RecordPort records;
    @Autowired SheepService sheep;
    @Autowired PlatformTransactionManager tx;
    @Autowired JdbcTemplate jdbc;
    @MockitoSpyBean MeetingCardService cardService;

    MockMvc mvc() {
        return MockMvcBuilders.webAppContextSetup(context).build();
    }

    User user() {
        User u = users.save(new User("me-user-" + SEQ.incrementAndGet(), "x", "내 양"));
        new TransactionTemplate(tx).execute(s -> sheep.awardRecord(u.getId(), RECORD.incrementAndGet()));
        return u;
    }

    String bearer(User u) {
        return "Bearer " + tokens.issue(u.getId());
    }

    long createMeeting(User host, String type) throws Exception {
        String json = "{\"type\":\"" + type + "\",\"title\":\"종료 테스트 " + SEQ.incrementAndGet()
                + "\",\"category\":\"RELATION_FAMILY\",\"capacity\":6}";
        String res = mvc().perform(post("/api/meetings").header("Authorization", bearer(host))
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(res, "$.data.meetingId")).longValue();
    }

    void join(User u, long id) throws Exception {
        mvc().perform(post("/api/meetings/" + id + "/participants").header("Authorization", bearer(u)))
                .andExpect(status().isOk());
    }

    /** 실시간 모임을 시작한다. 방장 + 손님들. */
    long startedLive(User host, User... guests) throws Exception {
        long id = createMeeting(host, "LIVE");
        for (User g : guests) {
            join(g, id);
        }
        mvc().perform(post("/api/meetings/" + id + "/start").header("Authorization", bearer(host)))
                .andExpect(status().isOk());
        return id;
    }

    ResultActions end(User u, long id) throws Exception {
        return mvc().perform(post("/api/meetings/" + id + "/end").header("Authorization", bearer(u)));
    }

    ResultActions poll(User u, long id) throws Exception {
        return mvc().perform(get("/api/meetings/" + id + "/messages").header("Authorization", bearer(u)));
    }

    void expectError(ResultActions r, int status, String code) throws Exception {
        r.andExpect(status().is(status)).andExpect(jsonPath("$.error.code").value(code));
    }

    Meeting load(long id) {
        return meetings.findById(id).orElseThrow();
    }

    List<MeetingMessage> endedMessages(long id) {
        return messages.findByMeetingIdOrderByIdAsc(id).stream()
                .filter(m -> m.getType() == MessageType.SYSTEM && m.getContent().contains("모임이 끝났어요")).toList();
    }

    /** 그림 단계로 넘긴 뒤 그 사람의 기록(record)에 묶인 그림을 올린다. 올린 그림 id를 돌려준다. */
    long shareDrawing(User u, long id) throws Exception {
        long recordId = records.create(u.getId());
        Drawing d = drawings.save(new Drawing(recordId, u.getId(), 1L, Drawing.InputType.UPLOAD, "내 그림",
                "me-" + SEQ.incrementAndGet() + ".png", "image/png", 10));
        mvc().perform(post("/api/meetings/" + id + "/messages").header("Authorization", bearer(u))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"DRAWING\",\"drawingId\":" + d.getId() + "}"))
                .andExpect(status().isOk());
        return d.getId();
    }

    void say(User u, long id, String text) throws Exception {
        mvc().perform(post("/api/meetings/" + id + "/messages").header("Authorization", bearer(u))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"type\":\"TEXT\",\"content\":\"" + text + "\"}"))
                .andExpect(status().isOk());
    }

    void toDrawingPhase(User host, long id) throws Exception {
        mvc().perform(post("/api/meetings/" + id + "/phase/next").header("Authorization", bearer(host)))
                .andExpect(status().isOk());
    }

    // ---- 동의 · 종료 ----

    @Test
    void countsAgreementsAndEndsOnlyWhenEveryoneAgreed() throws Exception {
        User host = user(), a = user(), b = user();
        long id = startedLive(host, a, b);

        end(host, id).andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.meetingId").value(id))
                .andExpect(jsonPath("$.data.endAgreedCount").value(1))
                .andExpect(jsonPath("$.data.participantCount").value(3))
                .andExpect(jsonPath("$.data.status").value("IN_PROGRESS"));
        // 같은 사람이 여러 번 눌러도 1번
        end(host, id).andExpect(jsonPath("$.data.endAgreedCount").value(1))
                .andExpect(jsonPath("$.data.status").value("IN_PROGRESS"));

        poll(host, id).andExpect(jsonPath("$.data.endAgreedCount").value(1))
                .andExpect(jsonPath("$.data.endAgreed").value(true));
        poll(a, id).andExpect(jsonPath("$.data.endAgreedCount").value(1))
                .andExpect(jsonPath("$.data.endAgreed").value(false))
                .andExpect(jsonPath("$.data.status").value("IN_PROGRESS"));

        end(a, id).andExpect(jsonPath("$.data.endAgreedCount").value(2))
                .andExpect(jsonPath("$.data.status").value("IN_PROGRESS"));
        assertThat(load(id).getStatus()).isEqualTo(MeetingStatus.IN_PROGRESS);
        assertThat(endedMessages(id)).isEmpty();
        verify(cardService, times(0)).generateMeetingCards(anyLong());

        end(b, id).andExpect(jsonPath("$.data.endAgreedCount").value(3))
                .andExpect(jsonPath("$.data.participantCount").value(3))
                .andExpect(jsonPath("$.data.status").value("ENDED"));
        assertThat(load(id).getStatus()).isEqualTo(MeetingStatus.ENDED);
        assertThat(load(id).getEndedAt()).isNotNull();
    }

    @Test
    void endingLeavesOneAnnouncementAndClosesTheRoomForSending() throws Exception {
        User host = user(), guest = user();
        long id = startedLive(host, guest);
        end(host, id).andExpect(status().isOk());
        end(guest, id).andExpect(jsonPath("$.data.status").value("ENDED"));

        List<MeetingMessage> ended = endedMessages(id);
        assertThat(ended).hasSize(1);
        assertThat(ended.get(0).getContent()).isEqualTo("모두 동의해서 모임이 끝났어요.");
        assertThat(ended.get(0).getUserId()).isNull();

        poll(host, id).andExpect(jsonPath("$.data.status").value("ENDED"))
                .andExpect(jsonPath("$.data.deleteAt").isString())
                .andExpect(jsonPath("$.data.endAgreedCount").value(2))
                .andExpect(jsonPath("$.data.endAgreed").value(true));
        mvc().perform(post("/api/meetings/" + id + "/messages").header("Authorization", bearer(host))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"type\":\"TEXT\",\"content\":\"끝난 뒤\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("MEETING_NOT_IN_PROGRESS"));
    }

    @Test
    void pressingAgainAfterTheMeetingEndedIsHarmless() throws Exception {
        User host = user(), guest = user();
        long id = startedLive(host, guest);
        end(host, id).andExpect(status().isOk());
        end(guest, id).andExpect(jsonPath("$.data.status").value("ENDED"));

        // 마지막 사람이 두 번 눌러도, 먼저 누른 사람이 다시 눌러도 오류 없이 종료 상태를 받는다
        end(guest, id).andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("ENDED"))
                .andExpect(jsonPath("$.data.endAgreedCount").value(2));
        end(host, id).andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("ENDED"));

        assertThat(endedMessages(id)).hasSize(1); // 안내도 한 번
        verify(cardService, times(1)).generateMeetingCards(id); // 카드 생성도 한 번
    }

    @Test
    void canceledParticipantDoesNotBlockTheEnd() throws Exception {
        User host = user(), guest = user(), leaver = user();
        long id = createMeeting(host, "LIVE");
        join(guest, id);
        join(leaver, id);
        mvc().perform(delete("/api/meetings/" + id + "/participants/me").header("Authorization", bearer(leaver)))
                .andExpect(status().isOk());
        mvc().perform(post("/api/meetings/" + id + "/start").header("Authorization", bearer(host)))
                .andExpect(status().isOk());

        end(host, id).andExpect(jsonPath("$.data.participantCount").value(2))
                .andExpect(jsonPath("$.data.status").value("IN_PROGRESS"));
        end(guest, id).andExpect(jsonPath("$.data.status").value("ENDED"));
    }

    @Test
    void errorsAreMapped() throws Exception {
        User host = user(), guest = user(), outsider = user(), leaver = user();
        long id = createMeeting(host, "LIVE");
        join(guest, id);
        // 대기실: 아직 진행 중이 아님
        expectError(end(host, id), 409, "MEETING_NOT_IN_PROGRESS");
        join(leaver, id);
        mvc().perform(delete("/api/meetings/" + id + "/participants/me").header("Authorization", bearer(leaver)))
                .andExpect(status().isOk());
        mvc().perform(post("/api/meetings/" + id + "/start").header("Authorization", bearer(host)))
                .andExpect(status().isOk());

        expectError(end(outsider, id), 403, "NOT_JOINED");
        expectError(end(leaver, id), 403, "NOT_JOINED");
        expectError(end(host, 987_654_321L), 404, "MEETING_NOT_FOUND");
        mvc().perform(post("/api/meetings/" + id + "/end")).andExpect(status().isUnauthorized());
        assertThat(load(id).getStatus()).isEqualTo(MeetingStatus.IN_PROGRESS);

        // 취소된 모임
        User host2 = user(), guest2 = user();
        long canceled = createMeeting(host2, "LIVE");
        join(guest2, canceled);
        mvc().perform(delete("/api/meetings/" + canceled).header("Authorization", bearer(host2)))
                .andExpect(status().isOk());
        expectError(end(guest2, canceled), 409, "MEETING_NOT_IN_PROGRESS");

        // 종료 24시간이 지나 사라진 방
        end(host, id).andExpect(status().isOk());
        end(guest, id).andExpect(jsonPath("$.data.status").value("ENDED"));
        jdbc.update("update meetings set ended_at = ? where id = ?",
                Timestamp.valueOf(LocalDateTime.now(KST).minusHours(25)), id);
        expectError(end(host, id), 404, "MEETING_NOT_FOUND");
    }

    // ---- 회고 카드 ----

    @Test
    void cardsAreMadeAfterEndForParticipantsWhoSharedADrawing() throws Exception {
        User host = user(), guest = user(), quiet = user();
        long id = startedLive(host, guest, quiet);
        say(host, id, "오늘은 좀 지쳤어요");
        toDrawingPhase(host, id);
        long hostDrawing = shareDrawing(host, id);
        long guestDrawing = shareDrawing(guest, id);
        say(guest, id, "창문이 하나뿐인 방이에요");

        end(host, id).andExpect(status().isOk());
        end(guest, id).andExpect(status().isOk());
        assertThat(cardRepository.findByMeetingIdAndUserId(id, host.getId())).isEmpty(); // 아직 전원 동의 전
        end(quiet, id).andExpect(jsonPath("$.data.status").value("ENDED"));

        // 그림을 낸 사람만 카드가 생긴다(AI 키가 없으면 대체 카드)
        assertThat(cardRepository.findByMeetingIdAndUserId(id, host.getId())).isPresent();
        assertThat(cardRepository.findByMeetingIdAndUserId(id, guest.getId())).isPresent();
        assertThat(cardRepository.findByMeetingIdAndUserId(id, quiet.getId())).isEmpty();

        mvc().perform(get("/api/meetings/" + id + "/reflection").header("Authorization", bearer(host)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.drawing.drawingId").value(hostDrawing))
                .andExpect(jsonPath("$.data.card.whatISaid").isString());
        mvc().perform(get("/api/meetings/" + id + "/reflection").header("Authorization", bearer(guest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.drawing.drawingId").value(guestDrawing));
        // 그림을 안 낸 사람은 카드가 없는 게 정상
        mvc().perform(get("/api/meetings/" + id + "/reflection").header("Authorization", bearer(quiet)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NO_DRAWING_IN_MEETING"));
    }

    @Test
    void cardGenerationStartsOnlyAfterTheEndIsCommitted() throws Exception {
        User host = user(), guest = user();
        long id = startedLive(host, guest);
        AtomicReference<String> statusSeenByCardGeneration = new AtomicReference<>();
        doAnswer(invocation -> {
            // 카드 생성이 불릴 때 종료가 이미 다른 연결에서도 보이는 상태(=커밋 완료)여야 한다
            statusSeenByCardGeneration.set(
                    jdbc.queryForObject("select status from meetings where id = ?", String.class, id));
            return invocation.callRealMethod();
        }).when(cardService).generateMeetingCards(id);

        end(host, id).andExpect(status().isOk());
        end(guest, id).andExpect(jsonPath("$.data.status").value("ENDED"));

        assertThat(statusSeenByCardGeneration.get()).isEqualTo("ENDED");
    }

    @Test
    void failingCardGenerationNeverUndoesTheEnd() throws Exception {
        User host = user(), guest = user();
        long id = startedLive(host, guest);
        toDrawingPhase(host, id);
        shareDrawing(host, id);
        shareDrawing(guest, id);
        doThrow(new RuntimeException("AI 서버 오류")).when(cardService).generateMeetingCards(id);

        end(host, id).andExpect(status().isOk());
        // 카드 생성이 예외를 던져도 종료 요청은 성공하고, 종료는 그대로 남는다
        end(guest, id).andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("ENDED"));

        assertThat(load(id).getStatus()).isEqualTo(MeetingStatus.ENDED);
        assertThat(endedMessages(id)).hasSize(1);
        assertThat(cardRepository.findByMeetingIdAndUserId(id, host.getId())).isEmpty();
        poll(host, id).andExpect(jsonPath("$.data.status").value("ENDED"));
        verify(cardService, times(1)).generateMeetingCards(id); // 재시도 없이 한 번만 불렀다

        // 나중에 수동으로 다시 불러도 안전하다(카드 서비스는 한 장만 만든다)
        org.mockito.Mockito.doCallRealMethod().when(cardService).generateMeetingCards(id);
        cardService.generateMeetingCards(id);
        cardService.generateMeetingCards(id);
        assertThat(cardRepository.findByMeetingIdAndUserId(id, host.getId())).isPresent();
        assertThat(cardRepository.findByMeetingIdAndUserId(id, guest.getId())).isPresent();
    }

    // ---- 동시성 ----

    @Test
    void lastTwoAgreementsArrivingTogetherEndOnceAndGenerateCardsOnce() throws Exception {
        User host = user(), guest = user();
        long id = startedLive(host, guest);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();
        for (User u : new User[] {host, guest}) {
            futures.add(pool.submit(() -> {
                go.await();
                end(u, id).andExpect(status().isOk());
                return null;
            }));
        }
        go.countDown();
        for (Future<?> f : futures) {
            f.get();
        }
        pool.shutdown();

        assertThat(load(id).getStatus()).isEqualTo(MeetingStatus.ENDED);
        assertThat(endedMessages(id)).hasSize(1);
        verify(cardService, times(1)).generateMeetingCards(id);
    }

    @Test
    void agreementAcrossOffsetTimesUsesServerClock() throws Exception {
        // 시각 필드는 OffsetDateTime 직렬화 규칙(+09:00)을 그대로 쓴다
        User host = user(), guest = user();
        long id = startedLive(host, guest);
        end(host, id).andExpect(status().isOk());
        end(guest, id).andExpect(status().isOk());
        String deleteAt = JsonPath.read(poll(host, id).andReturn().getResponse().getContentAsString(), "$.data.deleteAt");
        assertThat(OffsetDateTime.parse(deleteAt).getOffset().getTotalSeconds()).isEqualTo(9 * 3600);
    }
}
