package com.ganzithon.moyang.meeting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ganzithon.moyang.auth.JwtTokenProvider;
import com.ganzithon.moyang.drawing.domain.Drawing;
import com.ganzithon.moyang.drawing.repository.DrawingRepository;
import com.ganzithon.moyang.meeting.domain.MeetingEnums.MessageType;
import com.ganzithon.moyang.meeting.domain.MeetingMessage;
import com.ganzithon.moyang.meeting.repository.MeetingMessageRepository;
import com.ganzithon.moyang.meeting.repository.MeetingRepository;
import com.ganzithon.moyang.meeting.service.MeetingLifecycleService;
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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.WebApplicationContext;

/** 모임 단계 진행: 시작 안내 · 주제 건너뛰기(phase/next) · 그림 모이면 자동 진행 · INTRO에서는 그림 공유 불가. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:meeting-phase;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "moyang.upload-dir=build/test-uploads",
        "moyang.sheep.demo-daily-limit-off=true"
})
class MeetingPhaseApiTest {

    static final AtomicInteger SEQ = new AtomicInteger();
    static final AtomicLong RECORD = new AtomicLong(900_000);
    static final ZoneId KST = ZoneId.of("Asia/Seoul");

    @Autowired WebApplicationContext context;
    @Autowired JwtTokenProvider tokens;
    @Autowired UserRepository users;
    @Autowired MeetingRepository meetings;
    @Autowired MeetingMessageRepository messages;
    @Autowired DrawingRepository drawings;
    @Autowired SheepService sheep;
    @Autowired PlatformTransactionManager tx;
    @Autowired JdbcTemplate jdbc;
    @Autowired MeetingLifecycleService lifecycle;

    MockMvc mvc() {
        return MockMvcBuilders.webAppContextSetup(context).build();
    }

    LocalDateTime now() {
        return LocalDateTime.now(KST);
    }

    User user() {
        User u = users.save(new User("mp-user-" + SEQ.incrementAndGet(), "x", "내 양"));
        new TransactionTemplate(tx).execute(s -> sheep.awardRecord(u.getId(), RECORD.incrementAndGet()));
        return u;
    }

    String bearer(User u) {
        return "Bearer " + tokens.issue(u.getId());
    }

    long createMeeting(User host, String type, Integer startInMinutes) throws Exception {
        String json = "{\"type\":\"" + type + "\",\"title\":\"단계 테스트 " + SEQ.incrementAndGet()
                + "\",\"category\":\"RELATION_FAMILY\",\"capacity\":6"
                + (startInMinutes == null ? "" : ",\"startAt\":\""
                + OffsetDateTime.now(KST).plusMinutes(startInMinutes).withNano(0) + "\"") + "}";
        String res = mvc().perform(post("/api/meetings").header("Authorization", bearer(host))
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(res, "$.data.meetingId")).longValue();
    }

    void join(User u, long id) throws Exception {
        mvc().perform(post("/api/meetings/" + id + "/participants").header("Authorization", bearer(u)))
                .andExpect(status().isOk());
    }

    ResultActions start(User u, long id) throws Exception {
        return mvc().perform(post("/api/meetings/" + id + "/start").header("Authorization", bearer(u)));
    }

    ResultActions enter(User u, long id) throws Exception {
        return mvc().perform(post("/api/meetings/" + id + "/enter").header("Authorization", bearer(u)));
    }

    ResultActions next(User u, long id) throws Exception {
        return mvc().perform(post("/api/meetings/" + id + "/phase/next").header("Authorization", bearer(u)));
    }

    ResultActions poll(User u, long id) throws Exception {
        return mvc().perform(get("/api/meetings/" + id + "/messages").header("Authorization", bearer(u)));
    }

    ResultActions sendDrawing(User u, long id, Drawing d) throws Exception {
        return mvc().perform(post("/api/meetings/" + id + "/messages").header("Authorization", bearer(u))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"type\":\"DRAWING\",\"drawingId\":" + d.getId() + "}"));
    }

    Drawing drawingOf(User u) {
        return drawings.save(new Drawing(RECORD.incrementAndGet(), u.getId(), 1L, Drawing.InputType.UPLOAD,
                "내 그림", "mp-" + SEQ.incrementAndGet() + ".png", "image/png", 10));
    }

    void expectError(ResultActions r, int status, String code) throws Exception {
        r.andExpect(status().is(status)).andExpect(jsonPath("$.error.code").value(code));
    }

    String phaseOf(long id) {
        return meetings.findById(id).orElseThrow().getPhase().name();
    }

    List<MeetingMessage> systemMessages(long id) {
        return messages.findByMeetingIdOrderByIdAsc(id).stream().filter(m -> m.getType() == MessageType.SYSTEM).toList();
    }

    /** 실시간 모임: 방장 + 손님들을 넣고 시작한다. */
    long startedLive(User host, User... guests) throws Exception {
        long id = createMeeting(host, "LIVE", null);
        for (User g : guests) {
            join(g, id);
        }
        start(host, id).andExpect(status().isOk());
        return id;
    }

    /** 예약 모임: 방장 + 신청자들, 시작 시각은 입장이 열린 3분 뒤로 옮겨 둔다. */
    long openScheduled(User host, User... guests) throws Exception {
        long id = createMeeting(host, "SCHEDULED", 120);
        for (User g : guests) {
            join(g, id);
        }
        jdbc.update("update meetings set start_at = ? where id = ?", Timestamp.valueOf(now().plusMinutes(3)), id);
        return id;
    }

    void moveStartAtToPast(long id) {
        jdbc.update("update meetings set start_at = ? where id = ?", Timestamp.valueOf(now().minusSeconds(10)), id);
    }

    // ---- 시작 안내 ----

    @Test
    void liveStartLeavesExactlyOneIntroMessage() throws Exception {
        User host = user(), guest = user();
        long id = startedLive(host, guest);

        List<MeetingMessage> sys = systemMessages(id);
        assertThat(sys).hasSize(1);
        assertThat(sys.get(0).getUserId()).isNull();
        poll(guest, id).andExpect(jsonPath("$.data.messages.length()").value(1))
                .andExpect(jsonPath("$.data.messages[0].type").value("SYSTEM"))
                .andExpect(jsonPath("$.data.messages[0].content").value(containsString("자기소개")));
    }

    @Test
    void scheduledStartLeavesIntroMessageOnlyOnceEvenWhenSchedulerRunsAgain() throws Exception {
        User host = user(), a = user(), b = user();
        long id = openScheduled(host, a, b);
        enter(a, id).andExpect(status().isOk());
        enter(b, id).andExpect(status().isOk());
        moveStartAtToPast(id);

        lifecycle.processDue(now());
        lifecycle.processDue(now());

        assertThat(systemMessages(id)).hasSize(1);
        assertThat(phaseOf(id)).isEqualTo("INTRO");
    }

    @Test
    void startByTheSecondEntryAfterStartTimeLeavesIntroMessage() throws Exception {
        User host = user(), guest = user();
        long id = openScheduled(host, guest);
        enter(host, id).andExpect(status().isOk());
        assertThat(systemMessages(id)).isEmpty(); // 아직 시작 전
        moveStartAtToPast(id);
        enter(guest, id).andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("IN_PROGRESS"));
        enter(guest, id).andExpect(status().isOk());

        assertThat(systemMessages(id)).hasSize(1);
    }

    @Test
    void waitingRoomAndAutoCancelLeaveNoMessages() throws Exception {
        User host = user(), guest = user();
        long id = createMeeting(host, "LIVE", null);
        join(guest, id);
        assertThat(messages.findByMeetingIdOrderByIdAsc(id)).isEmpty();
    }

    // ---- 주제 건너뛰기 ----

    @Test
    void hostWalksThroughEveryPhaseAndEachStepLeavesAnnouncement() throws Exception {
        User host = user(), guest = user();
        long id = startedLive(host, guest);

        next(host, id).andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.meetingId").value(id))
                .andExpect(jsonPath("$.data.phase").value("DRAWING"))
                .andExpect(jsonPath("$.data.topic").isString());
        next(host, id).andExpect(jsonPath("$.data.phase").value("SHARING"));
        next(host, id).andExpect(jsonPath("$.data.phase").value("CLOSING"));
        expectError(next(host, id), 409, "LAST_PHASE");
        assertThat(phaseOf(id)).isEqualTo("CLOSING");

        List<MeetingMessage> sys = systemMessages(id);
        assertThat(sys).hasSize(4); // 시작 + 단계 3번
        assertThat(sys.get(1).getContent()).contains("오늘의 주제는").contains("그림");
        assertThat(sys.get(2).getContent()).contains("이야기");
        assertThat(sys.get(3).getContent()).contains("마무리");

        poll(guest, id).andExpect(jsonPath("$.data.phase").value("CLOSING"))
                .andExpect(jsonPath("$.data.currentStep").value(4))
                .andExpect(jsonPath("$.data.messages.length()").value(4));
    }

    @Test
    void drawingAnnouncementContainsTheMeetingTopic() throws Exception {
        User host = user(), guest = user();
        long id = startedLive(host, guest);
        String topic = meetings.findById(id).orElseThrow().getTopicTitle();

        next(host, id).andExpect(jsonPath("$.data.topic").value(topic));
        assertThat(systemMessages(id).get(1).getContent()).contains(topic);
    }

    @Test
    void skipErrorsAreMapped() throws Exception {
        User host = user(), guest = user(), outsider = user(), leaver = user(), waitingHost = user();
        long waiting = createMeeting(waitingHost, "LIVE", null);
        join(guest, waiting);
        // 대기실: 아직 진행 중이 아님
        expectError(next(waitingHost, waiting), 409, "MEETING_NOT_IN_PROGRESS");
        mvc().perform(delete("/api/meetings/" + waiting + "/participants/me").header("Authorization", bearer(guest)))
                .andExpect(status().isOk()); // 같은 손님이 아래 모임에도 들어갈 수 있게 양털을 돌려받는다

        long id = createMeeting(host, "LIVE", null);
        join(guest, id);
        join(leaver, id);
        mvc().perform(delete("/api/meetings/" + id + "/participants/me").header("Authorization", bearer(leaver)))
                .andExpect(status().isOk());
        start(host, id).andExpect(status().isOk());

        expectError(next(guest, id), 403, "NO_SKIP_PERMISSION");
        expectError(next(outsider, id), 403, "NOT_PARTICIPANT");
        expectError(next(leaver, id), 403, "NOT_PARTICIPANT");
        expectError(next(host, 987_654_321L), 404, "MEETING_NOT_FOUND");
        mvc().perform(post("/api/meetings/" + id + "/phase/next")).andExpect(status().isUnauthorized());
        assertThat(phaseOf(id)).isEqualTo("INTRO"); // 실패한 요청은 단계를 바꾸지 않았다
        assertThat(systemMessages(id)).hasSize(1);

        jdbc.update("update meetings set status = 'ENDED', ended_at = ? where id = ?",
                Timestamp.valueOf(now().minusHours(1)), id);
        expectError(next(host, id), 409, "MEETING_NOT_IN_PROGRESS");
        jdbc.update("update meetings set ended_at = ? where id = ?", Timestamp.valueOf(now().minusHours(25)), id);
        expectError(next(host, id), 404, "MEETING_NOT_FOUND");
    }

    @Test
    void scheduledMeetingWithoutHostLetsOnlyTheFirstEntrantSkip() throws Exception {
        User host = user(), a = user(), b = user();
        long id = openScheduled(host, a, b);
        enter(a, id).andExpect(status().isOk());
        enter(b, id).andExpect(status().isOk());
        moveStartAtToPast(id);
        lifecycle.processDue(now());

        expectError(next(host, id), 403, "NO_SKIP_PERMISSION"); // 방장은 입장하지 않았으니 권한 없음
        expectError(next(b, id), 403, "NO_SKIP_PERMISSION");
        next(a, id).andExpect(status().isOk()).andExpect(jsonPath("$.data.phase").value("DRAWING"));
    }

    @Test
    void hostSkipsOnAScheduledMeetingWhenHostEntered() throws Exception {
        User host = user(), a = user();
        long id = openScheduled(host, a);
        enter(a, id).andExpect(status().isOk());
        enter(host, id).andExpect(status().isOk());
        moveStartAtToPast(id);
        lifecycle.processDue(now());

        expectError(next(a, id), 403, "NO_SKIP_PERMISSION");
        next(host, id).andExpect(status().isOk()).andExpect(jsonPath("$.data.phase").value("DRAWING"));
    }

    // ---- INTRO에서는 그림 공유 불가 ----

    @Test
    void drawingCannotBeSharedDuringIntroButTextCan() throws Exception {
        User host = user(), guest = user();
        long id = startedLive(host, guest);
        Drawing mine = drawingOf(guest);

        expectError(sendDrawing(guest, id, mine), 409, "DRAWING_NOT_OPEN");
        // 다른 사람 그림이어도, 없는 그림이어도 단계 오류가 먼저다
        expectError(sendDrawing(guest, id, drawingOf(host)), 409, "DRAWING_NOT_OPEN");
        assertThat(messages.findByMeetingIdOrderByIdAsc(id).stream().filter(m -> m.getType() == MessageType.DRAWING))
                .isEmpty();
        mvc().perform(post("/api/meetings/" + id + "/messages").header("Authorization", bearer(guest))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"type\":\"TEXT\",\"content\":\"안녕하세요\"}"))
                .andExpect(status().isOk());

        next(host, id).andExpect(status().isOk());
        sendDrawing(guest, id, mine).andExpect(status().isOk());
    }

    // ---- 그림이 다 모이면 자동 진행 ----

    @Test
    void everyoneSubmittingMovesToSharingAutomatically() throws Exception {
        User host = user(), a = user(), b = user();
        long id = startedLive(host, a, b);
        next(host, id).andExpect(jsonPath("$.data.phase").value("DRAWING"));
        int before = systemMessages(id).size();

        sendDrawing(a, id, drawingOf(a)).andExpect(status().isOk());
        sendDrawing(a, id, drawingOf(a)).andExpect(status().isOk()); // 같은 사람이 또 올려도 1명
        sendDrawing(host, id, drawingOf(host)).andExpect(status().isOk());
        assertThat(phaseOf(id)).isEqualTo("DRAWING");
        assertThat(systemMessages(id)).hasSize(before);

        sendDrawing(b, id, drawingOf(b)).andExpect(status().isOk());
        assertThat(phaseOf(id)).isEqualTo("SHARING");
        List<MeetingMessage> sys = systemMessages(id);
        assertThat(sys).hasSize(before + 1);
        assertThat(sys.get(sys.size() - 1).getContent()).contains("이야기");

        poll(a, id).andExpect(jsonPath("$.data.phase").value("SHARING"))
                .andExpect(jsonPath("$.data.currentStep").value(3))
                .andExpect(jsonPath("$.data.submittedDrawingCount").value(3))
                .andExpect(jsonPath("$.data.participantCount").value(3));

        // 이미 SHARING이면 그림을 더 올려도 더 넘어가지 않는다
        sendDrawing(b, id, drawingOf(b)).andExpect(status().isOk());
        assertThat(phaseOf(id)).isEqualTo("SHARING");
        // 버튼으로는 계속 넘길 수 있다
        next(host, id).andExpect(jsonPath("$.data.phase").value("CLOSING"));
    }

    @Test
    void skippingPastDrawingMeansLateDrawingsDoNotAdvanceAgain() throws Exception {
        User host = user(), guest = user();
        long id = startedLive(host, guest);
        next(host, id).andExpect(status().isOk());
        next(host, id).andExpect(jsonPath("$.data.phase").value("SHARING")); // 그림 없이 건너뜀

        sendDrawing(guest, id, drawingOf(guest)).andExpect(status().isOk());
        sendDrawing(host, id, drawingOf(host)).andExpect(status().isOk());
        assertThat(phaseOf(id)).isEqualTo("SHARING");
    }

    @Test
    void canceledParticipantIsNotWaitedFor() throws Exception {
        User host = user(), guest = user(), leaver = user();
        long id = createMeeting(host, "LIVE", null);
        join(guest, id);
        join(leaver, id);
        mvc().perform(delete("/api/meetings/" + id + "/participants/me").header("Authorization", bearer(leaver)))
                .andExpect(status().isOk());
        start(host, id).andExpect(status().isOk());
        next(host, id).andExpect(status().isOk());

        sendDrawing(guest, id, drawingOf(guest)).andExpect(status().isOk());
        assertThat(phaseOf(id)).isEqualTo("DRAWING");
        sendDrawing(host, id, drawingOf(host)).andExpect(status().isOk());
        assertThat(phaseOf(id)).isEqualTo("SHARING"); // 신청을 취소한 사람은 기다리지 않는다
    }

    // ---- 동시성 ----

    @Test
    void manyFastClicksAdvanceEachPhaseExactlyOnce() throws Exception {
        User host = user(), guest = user();
        long id = startedLive(host, guest);
        int clicks = 8;
        ExecutorService pool = Executors.newFixedThreadPool(clicks);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<Integer>> results = new ArrayList<>();
        for (int i = 0; i < clicks; i++) {
            results.add(pool.submit(() -> {
                go.await();
                return next(host, id).andReturn().getResponse().getStatus();
            }));
        }
        go.countDown();
        int ok = 0;
        int conflict = 0;
        for (Future<Integer> f : results) {
            int s = f.get();
            if (s == 200) {
                ok++;
            } else if (s == 409) {
                conflict++;
            }
        }
        pool.shutdown();

        // INTRO → CLOSING까지 3번만 성공하고 나머지는 LAST_PHASE(409). 안내 메시지도 단계 수만큼만 남는다.
        assertThat(ok).isEqualTo(3);
        assertThat(conflict).isEqualTo(clicks - 3);
        assertThat(phaseOf(id)).isEqualTo("CLOSING");
        assertThat(systemMessages(id)).hasSize(4);
    }

    @Test
    void lastTwoDrawingsArrivingTogetherAdvanceOnlyOnce() throws Exception {
        User host = user(), guest = user();
        long id = startedLive(host, guest);
        next(host, id).andExpect(status().isOk());
        int before = systemMessages(id).size();
        Drawing hd = drawingOf(host);
        Drawing gd = drawingOf(guest);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();
        futures.add(pool.submit(() -> {
            go.await();
            sendDrawing(host, id, hd).andExpect(status().isOk());
            return null;
        }));
        futures.add(pool.submit(() -> {
            go.await();
            sendDrawing(guest, id, gd).andExpect(status().isOk());
            return null;
        }));
        go.countDown();
        for (Future<?> f : futures) {
            f.get();
        }
        pool.shutdown();

        assertThat(phaseOf(id)).isEqualTo("SHARING");
        assertThat(systemMessages(id)).hasSize(before + 1);
    }
}
