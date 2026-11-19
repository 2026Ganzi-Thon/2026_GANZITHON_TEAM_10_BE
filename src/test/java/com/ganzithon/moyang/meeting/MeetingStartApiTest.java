package com.ganzithon.moyang.meeting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ganzithon.moyang.auth.JwtTokenProvider;
import com.ganzithon.moyang.meeting.domain.Meeting;
import com.ganzithon.moyang.meeting.domain.MeetingEnums.CancelReason;
import com.ganzithon.moyang.meeting.domain.MeetingEnums.MeetingStatus;
import com.ganzithon.moyang.meeting.domain.MeetingEnums.MeetingType;
import com.ganzithon.moyang.meeting.domain.MeetingParticipant;
import com.ganzithon.moyang.meeting.repository.MeetingParticipantRepository;
import com.ganzithon.moyang.meeting.repository.MeetingRepository;
import com.ganzithon.moyang.meeting.service.MeetingLifecycleService;
import com.ganzithon.moyang.meeting.service.MeetingPurgeService;
import com.ganzithon.moyang.meeting.service.MeetingQueryService;
import com.ganzithon.moyang.meeting.service.MeetingScheduler;
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

/**
 * 실시간 시작 · 예약 입장 · 자동 시작/취소 스케줄러. 시간이 중요한 경우는 모임의 startAt을 DB에서 직접 옮겨
 * 같은 시나리오를 만든다. 스케줄러 Bean은 테스트에서 꺼져 있고(build.gradle), 필요한 곳에서 직접 부른다.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:meeting-start;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "moyang.upload-dir=build/test-uploads",
        "moyang.sheep.demo-daily-limit-off=true"
})
class MeetingStartApiTest {

    static final AtomicInteger SEQ = new AtomicInteger();
    static final AtomicLong RECORD = new AtomicLong(700_000);
    static final ZoneId KST = ZoneId.of("Asia/Seoul");

    @Autowired WebApplicationContext context;
    @Autowired JwtTokenProvider tokens;
    @Autowired UserRepository users;
    @Autowired MeetingRepository meetings;
    @Autowired MeetingParticipantRepository participants;
    @Autowired SheepService sheep;
    @Autowired PlatformTransactionManager tx;
    @Autowired JdbcTemplate jdbc;
    @Autowired MeetingLifecycleService lifecycle;
    @Autowired MeetingQueryService time;
    @Autowired MeetingPurgeService purge;

    MockMvc mvc() {
        return MockMvcBuilders.webAppContextSetup(context).build();
    }

    LocalDateTime now() {
        return LocalDateTime.now(KST);
    }

    User user(int sheepCount) {
        User u = users.save(new User("ms-user-" + SEQ.incrementAndGet(), "x", "내 양"));
        for (int i = 0; i < sheepCount; i++) {
            new TransactionTemplate(tx).execute(s -> sheep.awardRecord(u.getId(), RECORD.incrementAndGet()));
        }
        return u;
    }

    String bearer(User u) {
        return "Bearer " + tokens.issue(u.getId());
    }

    int balance(User u) {
        return sheep.balance(u.getId()).balance();
    }

    long createMeeting(User host, String type, Integer startInMinutes) throws Exception {
        String json = "{\"type\":\"" + type + "\",\"title\":\"시작 테스트 " + SEQ.incrementAndGet()
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

    ResultActions detail(User u, long id) throws Exception {
        return mvc().perform(get("/api/meetings/" + id).header("Authorization", bearer(u)));
    }

    /** 모임 시작 시각을 DB에서 직접 옮긴다(시간 시나리오 만들기). */
    void moveStartAt(long id, LocalDateTime startAt) {
        jdbc.update("update meetings set start_at = ? where id = ?", Timestamp.valueOf(startAt), id);
    }

    Meeting load(long id) {
        return meetings.findById(id).orElseThrow();
    }

    /** 예약 모임을 만들고(방장 포함) 신청자들을 넣는다. startAt은 입장이 열린 3분 뒤로 옮겨 둔다. */
    long openScheduled(User host, User... guests) throws Exception {
        long id = createMeeting(host, "SCHEDULED", 120);
        for (User g : guests) {
            join(g, id);
        }
        moveStartAt(id, now().plusMinutes(3));
        return id;
    }

    // ---------- 실시간 시작 ----------

    @Test
    void hostStartsLiveMeetingAndNobodyIsChargedAgain() throws Exception {
        User host = user(2), guest = user(1);
        long id = createMeeting(host, "LIVE", null);
        join(guest, id);
        assertThat(balance(host)).isEqualTo(1);
        assertThat(balance(guest)).isZero();

        start(host, id).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.meetingId").value(id))
                .andExpect(jsonPath("$.data.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.data.phase").value("INTRO"))
                .andExpect(jsonPath("$.data.participantCount").value(2));
        assertThat(balance(host)).isEqualTo(1);
        assertThat(balance(guest)).isZero();

        // 다른 참여자는 상세 폴링으로 시작을 안다
        detail(guest, id).andExpect(jsonPath("$.data.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.data.phase").value("INTRO"));
        // 목록에서 빠지고, 새로 들어올 수 없다
        String list = mvc().perform(get("/api/meetings?type=LIVE").header("Authorization", bearer(host)))
                .andReturn().getResponse().getContentAsString();
        assertThat((List<Integer>) JsonPath.read(list, "$.data.meetings[*].meetingId")).doesNotContain((int) id);
        User late = user(1);
        mvc().perform(post("/api/meetings/" + id + "/participants").header("Authorization", bearer(late)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("MEETING_NOT_JOINABLE"));
        // 시작 뒤에는 나가기·취소가 안 된다
        mvc().perform(delete("/api/meetings/" + id + "/participants/me").header("Authorization", bearer(guest)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("ALREADY_STARTED"));
        assertThat(balance(guest)).isZero();
    }

    @Test
    void startErrorsAreMapped() throws Exception {
        User host = user(1), guest = user(1);
        long alone = createMeeting(host, "LIVE", null);
        start(host, alone).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("NOT_ENOUGH_PARTICIPANTS"));

        join(guest, alone);
        start(guest, alone).andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("NOT_HOST"));
        start(host, 99999999L).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("MEETING_NOT_FOUND"));
        mvc().perform(post("/api/meetings/" + alone + "/start")).andExpect(status().isUnauthorized());

        // 참여자가 나가서 혼자가 되면 시작할 수 없다
        mvc().perform(delete("/api/meetings/" + alone + "/participants/me").header("Authorization", bearer(guest)))
                .andExpect(status().isOk());
        start(host, alone).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("NOT_ENOUGH_PARTICIPANTS"));

        join(user(1), alone);
        start(host, alone).andExpect(status().isOk());
        start(host, alone).andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("ALREADY_STARTED"));

        // 예약 모임은 직접 시작할 수 없다
        long scheduled = createMeeting(user(1), "SCHEDULED", 120);
        User scheduledHost = users.findById(load(scheduled).getHostUserId()).orElseThrow();
        start(scheduledHost, scheduled).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("NOT_LIVE_MEETING"));

        // 취소된 모임
        long canceled = createMeeting(user(1), "LIVE", null);
        User canceledHost = users.findById(load(canceled).getHostUserId()).orElseThrow();
        mvc().perform(delete("/api/meetings/" + canceled).header("Authorization", bearer(canceledHost)))
                .andExpect(status().isOk());
        start(canceledHost, canceled).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("ALREADY_STARTED"));
    }

    // ---------- 예약 입장 ----------

    @Test
    void enterOpensFiveMinutesBeforeAndIsIdempotent() throws Exception {
        User host = user(1), guest = user(1);
        long id = createMeeting(host, "SCHEDULED", 120);
        join(guest, id);

        moveStartAt(id, now().plusMinutes(10));
        enter(host, id).andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("ENTER_NOT_OPEN"));

        moveStartAt(id, now().plusMinutes(3));
        enter(host, id).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.meetingId").value(id))
                .andExpect(jsonPath("$.data.status").value("OPEN"))
                .andExpect(jsonPath("$.data.enteredCount").value(1))
                .andExpect(jsonPath("$.data.startAt").exists());
        // 같은 사람이 다시 눌러도 입장자는 1명
        enter(host, id).andExpect(status().isOk()).andExpect(jsonPath("$.data.enteredCount").value(1));
        enter(guest, id).andExpect(status().isOk()).andExpect(jsonPath("$.data.enteredCount").value(2))
                // 시작 시각 전이라 아직 시작하지 않는다
                .andExpect(jsonPath("$.data.status").value("OPEN"));
        assertThat(load(id).getStatus()).isEqualTo(MeetingStatus.RECRUITING);
    }

    @Test
    void enterErrorsAreMapped() throws Exception {
        User host = user(1), guest = user(1), stranger = user(1), leaver = user(1);
        long id = createMeeting(host, "SCHEDULED", 120);
        join(guest, id);
        join(leaver, id);
        mvc().perform(delete("/api/meetings/" + id + "/participants/me").header("Authorization", bearer(leaver)))
                .andExpect(status().isOk());
        moveStartAt(id, now().plusMinutes(3));

        enter(stranger, id).andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("NOT_JOINED"));
        enter(leaver, id).andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("NOT_JOINED"));
        enter(host, 99999999L).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("MEETING_NOT_FOUND"));
        mvc().perform(post("/api/meetings/" + id + "/enter")).andExpect(status().isUnauthorized());

        long live = createMeeting(user(1), "LIVE", null);
        User liveHost = users.findById(load(live).getHostUserId()).orElseThrow();
        enter(liveHost, live).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("NOT_SCHEDULED_MEETING"));

        // 방장이 취소한 모임은 들어갈 수 없다
        mvc().perform(delete("/api/meetings/" + id).header("Authorization", bearer(host))).andExpect(status().isOk());
        enter(guest, id).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("MEETING_NOT_JOINABLE"));
    }

    @Test
    void secondEntryAfterStartTimeStartsTheMeetingRightAway() throws Exception {
        User host = user(1), guest = user(1);
        long id = openScheduled(host, guest);
        enter(host, id).andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("OPEN"));

        // 시작 시각이 1분 지났지만 아직 2분 대기 시간 안: 입장자가 2명이 되는 순간 시작한다
        moveStartAt(id, now().minusMinutes(1));
        enter(guest, id).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.data.phase").value("INTRO"))
                .andExpect(jsonPath("$.data.enteredCount").value(2));
        assertThat(load(id).getStatus()).isEqualTo(MeetingStatus.IN_PROGRESS);
        // 시작해도 양털은 그대로(추가 차감도 환불도 없다)
        assertThat(balance(host) + balance(guest)).isZero();
    }

    @Test
    void enteringAfterTheWaitHasExpiredIsRejectedAndNotRecorded() throws Exception {
        User host = user(1), guest = user(1);
        long id = openScheduled(host, guest);
        moveStartAt(id, now().minusMinutes(3));
        enter(guest, id).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("MEETING_NOT_JOINABLE"));
        assertThat(participants.countByMeetingIdAndCanceledAtIsNullAndEnteredAtIsNotNull(id)).isZero();
    }

    @Test
    void afterStartOnlyThoseWhoAlreadyEnteredCanComeBack() throws Exception {
        User host = user(1), a = user(1), b = user(1);
        long id = openScheduled(host, a, b);
        enter(host, id).andExpect(status().isOk());
        enter(a, id).andExpect(status().isOk());
        moveStartAt(id, now().minusSeconds(10));
        lifecycle.processDue(now());
        assertThat(load(id).getStatus()).isEqualTo(MeetingStatus.IN_PROGRESS);

        enter(a, id).andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("IN_PROGRESS"));
        enter(b, id).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("MEETING_NOT_JOINABLE"));
    }

    // ---------- 스케줄러 (자동 시작 · 자동 취소) ----------

    @Test
    void dueMeetingWithTwoEnteredStartsEvenWithoutHostAndFirstEntrantCanSkip() throws Exception {
        User host = user(1), a = user(1), b = user(1);
        long id = openScheduled(host, a, b);
        enter(a, id).andExpect(status().isOk());
        enter(b, id).andExpect(status().isOk());
        moveStartAt(id, now().minusSeconds(10));

        lifecycle.processDue(now());

        Meeting m = load(id);
        assertThat(m.getStatus()).isEqualTo(MeetingStatus.IN_PROGRESS);
        assertThat(m.getPhase().name()).isEqualTo("INTRO");
        assertThat(m.getStartedAt()).isNotNull();
        // 양털은 그대로(시작 때 추가 차감도 환불도 없다)
        assertThat(balance(host)).isZero();
        assertThat(balance(a)).isZero();
        assertThat(balance(b)).isZero();
        // 방장이 없으니 '주제 건너뛰기' 권한은 가장 먼저 입장한 a
        detail(a, id).andExpect(jsonPath("$.data.canSkip").value(true))
                .andExpect(jsonPath("$.data.phase").value("INTRO"));
        detail(b, id).andExpect(jsonPath("$.data.canSkip").value(false));
        detail(host, id).andExpect(jsonPath("$.data.canSkip").value(false));
    }

    @Test
    void hostKeepsSkipPermissionWhenHostHasEntered() throws Exception {
        User host = user(1), a = user(1);
        long id = openScheduled(host, a);
        enter(a, id).andExpect(status().isOk());
        enter(host, id).andExpect(status().isOk()); // 방장이 나중에 입장해도 방장이 권한자
        moveStartAt(id, now().minusSeconds(10));
        lifecycle.processDue(now());

        detail(host, id).andExpect(jsonPath("$.data.canSkip").value(true));
        detail(a, id).andExpect(jsonPath("$.data.canSkip").value(false));
    }

    @Test
    void liveHostCanSkipAfterStart() throws Exception {
        User host = user(1), guest = user(1);
        long id = createMeeting(host, "LIVE", null);
        join(guest, id);
        detail(host, id).andExpect(jsonPath("$.data.canSkip").value(false)).andExpect(jsonPath("$.data.phase").doesNotExist());
        start(host, id).andExpect(status().isOk());
        detail(host, id).andExpect(jsonPath("$.data.canSkip").value(true));
        detail(guest, id).andExpect(jsonPath("$.data.canSkip").value(false));
    }

    @Test
    void notEnoughEnteredWaitsTwoMinutesByClockThenCancelsAndRefundsEveryone() throws Exception {
        User host = user(1), a = user(1), b = user(1);
        long id = openScheduled(host, a, b);
        enter(host, id).andExpect(status().isOk()); // 입장자 1명뿐
        // DB가 시각을 마이크로초로 반올림하므로 초 단위로 맞춰 둔다
        LocalDateTime startAt = now().minusSeconds(30).withNano(0);
        moveStartAt(id, startAt);
        assertThat(balance(host) + balance(a) + balance(b)).isZero();

        // 시작 시각이 지났지만 2분 안 → 계속 기다린다 (아직 아무 일도 없다)
        lifecycle.processDue(now());
        assertThat(load(id).getStatus()).isEqualTo(MeetingStatus.RECRUITING);
        lifecycle.processDue(startAt.plusMinutes(2).minusSeconds(1));
        assertThat(load(id).getStatus()).isEqualTo(MeetingStatus.RECRUITING);

        // 시작 시각 + 2분이 되면 자동 취소, 신청을 유지한 참여자와 방장 모두 전액 환불
        lifecycle.processDue(startAt.plusMinutes(2));
        Meeting m = load(id);
        assertThat(m.getStatus()).isEqualTo(MeetingStatus.CANCELED);
        assertThat(m.getCancelReason()).isEqualTo(CancelReason.NOT_ENOUGH_PARTICIPANTS);
        assertThat(balance(host)).isEqualTo(1);
        assertThat(balance(a)).isEqualTo(1);
        assertThat(balance(b)).isEqualTo(1);
        detail(a, id).andExpect(jsonPath("$.data.status").value("CANCELED"))
                .andExpect(jsonPath("$.data.cancelReason").value("NOT_ENOUGH_PARTICIPANTS"));

        // 스케줄러가 또 돌아도 환불이 두 번 되지 않는다
        lifecycle.processDue(startAt.plusMinutes(5));
        assertThat(balance(host)).isEqualTo(1);
        assertThat(balance(a)).isEqualTo(1);
    }

    @Test
    void guestWhoCanceledEarlierIsNotRefundedAgainByAutoCancel() throws Exception {
        User host = user(1), a = user(1), gone = user(1);
        long id = createMeeting(host, "SCHEDULED", 120);
        join(a, id);
        join(gone, id);
        mvc().perform(delete("/api/meetings/" + id + "/participants/me").header("Authorization", bearer(gone)))
                .andExpect(status().isOk());
        assertThat(balance(gone)).isEqualTo(1); // 10분 전 이전 취소 → 이미 환불됨
        moveStartAt(id, now().minusMinutes(5));

        lifecycle.processDue(now());
        assertThat(load(id).getStatus()).isEqualTo(MeetingStatus.CANCELED);
        assertThat(balance(gone)).isEqualTo(1);
        assertThat(balance(a)).isEqualTo(1);
        assertThat(balance(host)).isEqualTo(1);
    }

    @Test
    void aFailingMeetingDoesNotStopTheOthers() throws Exception {
        // 망가진 모임: 양털 내역(MEETING_CREATE/JOIN) 없이 저장돼 환불하면 REFUND_NOT_ELIGIBLE로 실패한다
        User brokenHost = user(0);
        Meeting broken = meetings.save(new Meeting(MeetingType.SCHEDULED, "망가진 모임", null, "LOVE",
                now().minusMinutes(10), 4, brokenHost.getId(), "주제", now().minusHours(1)));
        participants.save(new MeetingParticipant(broken.getId(), brokenHost.getId(), "방장 양", now().minusHours(1)));

        User host = user(1), guest = user(1);
        long ok = openScheduled(host, guest);
        moveStartAt(ok, now().minusMinutes(5));

        lifecycle.processDue(now());

        // 정상 모임은 처리됐고, 망가진 모임은 그대로 남아 다음 회차에 다시 시도된다
        assertThat(load(ok).getStatus()).isEqualTo(MeetingStatus.CANCELED);
        assertThat(balance(host)).isEqualTo(1);
        assertThat(balance(guest)).isEqualTo(1);
        assertThat(load(broken.getId()).getStatus()).isEqualTo(MeetingStatus.RECRUITING);
    }

    @Test
    void newSchedulerLeavesAlreadyStartedAndCanceledMeetingsAlone() throws Exception {
        // 시작된 모임
        User h1 = user(1), g1 = user(1), g2 = user(1);
        long started = openScheduled(h1, g1, g2);
        enter(g1, started).andExpect(status().isOk());
        enter(g2, started).andExpect(status().isOk());
        moveStartAt(started, now().minusSeconds(10));
        lifecycle.processDue(now());
        // 자동 취소된 모임
        User h2 = user(1), g3 = user(1);
        long canceled = openScheduled(h2, g3);
        moveStartAt(canceled, now().minusMinutes(5));
        lifecycle.processDue(now());

        Meeting s0 = load(started), c0 = load(canceled);
        assertThat(s0.getStatus()).isEqualTo(MeetingStatus.IN_PROGRESS);
        assertThat(c0.getStatus()).isEqualTo(MeetingStatus.CANCELED);
        List<Integer> before = List.of(balance(h1), balance(g1), balance(g2), balance(h2), balance(g3));

        // 서버를 다시 켠 것처럼 스케줄러를 새로 만들어 여러 번 돌려도 아무것도 바뀌지 않는다
        for (int i = 0; i < 3; i++) {
            new MeetingScheduler(lifecycle, time, purge).runOnce();
        }

        Meeting s1 = load(started), c1 = load(canceled);
        assertThat(s1.getStatus()).isEqualTo(MeetingStatus.IN_PROGRESS);
        assertThat(s1.getStartedAt()).isEqualTo(s0.getStartedAt());
        assertThat(s1.getPhase()).isEqualTo(s0.getPhase());
        assertThat(c1.getStatus()).isEqualTo(MeetingStatus.CANCELED);
        assertThat(c1.getEndedAt()).isEqualTo(c0.getEndedAt());
        assertThat(c1.getCancelReason()).isEqualTo(c0.getCancelReason());
        assertThat(List.of(balance(h1), balance(g1), balance(g2), balance(h2), balance(g3))).isEqualTo(before);
    }

    @Test
    void schedulersRunningAtTheSameTimeRefundOnlyOnce() throws Exception {
        for (int round = 0; round < 3; round++) {
            User host = user(1), guest = user(1);
            long id = openScheduled(host, guest);
            moveStartAt(id, now().minusMinutes(5));

            ExecutorService pool = Executors.newFixedThreadPool(2);
            CountDownLatch go = new CountDownLatch(1);
            List<Future<?>> runs = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                runs.add(pool.submit(() -> {
                    go.await();
                    lifecycle.processDue(now());
                    return null;
                }));
            }
            go.countDown();
            for (Future<?> f : runs) {
                f.get();
            }
            pool.shutdown();

            assertThat(load(id).getStatus()).isEqualTo(MeetingStatus.CANCELED);
            assertThat(balance(host)).isEqualTo(1);
            assertThat(balance(guest)).isEqualTo(1);
        }
    }
}
