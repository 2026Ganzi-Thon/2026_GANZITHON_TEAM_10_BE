package com.ganzithon.moyang.meeting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ganzithon.moyang.auth.JwtTokenProvider;
import com.ganzithon.moyang.meeting.domain.MeetingEnums.MeetingStatus;
import com.ganzithon.moyang.meeting.repository.MeetingParticipantRepository;
import com.ganzithon.moyang.meeting.repository.MeetingRepository;
import com.ganzithon.moyang.sheep.service.SheepService;
import com.ganzithon.moyang.user.domain.User;
import com.ganzithon.moyang.user.repository.UserRepository;
import com.jayway.jsonpath.JsonPath;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.WebApplicationContext;

/** 신청 취소 · 방장 취소 · 환불. 모임은 실제 API로 만들어 양털 내역(MEETING_CREATE/JOIN)이 남게 한다. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:meeting-cancel;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "moyang.upload-dir=build/test-uploads",
        "moyang.sheep.demo-daily-limit-off=true"
})
class MeetingCancelApiTest {

    static final AtomicInteger SEQ = new AtomicInteger();
    static final AtomicLong RECORD = new AtomicLong(900_000);
    static final ZoneId KST = ZoneId.of("Asia/Seoul");

    @Autowired WebApplicationContext context;
    @Autowired JwtTokenProvider tokens;
    @Autowired UserRepository users;
    @Autowired MeetingRepository meetings;
    @Autowired MeetingParticipantRepository participants;
    @Autowired SheepService sheep;
    @Autowired PlatformTransactionManager tx;

    MockMvc mvc() {
        return MockMvcBuilders.webAppContextSetup(context).build();
    }

    User user(int sheepCount) {
        User u = users.save(new User("mc-user-" + SEQ.incrementAndGet(), "x", "내 양"));
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

    String inMinutes(int minutes) {
        return OffsetDateTime.now(KST).plusMinutes(minutes).withNano(0).toString();
    }

    /** 방장이 실제 API로 모임을 만든다(양털 -1). */
    long createMeeting(User host, String type, Integer startInMinutes) throws Exception {
        String json = "{\"type\":\"" + type + "\",\"title\":\"취소 테스트 " + SEQ.incrementAndGet()
                + "\",\"category\":\"RELATION_FAMILY\",\"capacity\":6"
                + (startInMinutes == null ? "" : ",\"startAt\":\"" + inMinutes(startInMinutes) + "\"") + "}";
        String res = mvc().perform(post("/api/meetings").header("Authorization", bearer(host))
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(res, "$.data.meetingId")).longValue();
    }

    void join(User u, long meetingId) throws Exception {
        mvc().perform(post("/api/meetings/" + meetingId + "/participants").header("Authorization", bearer(u)))
                .andExpect(status().isOk());
    }

    ResultActions leave(User u, long meetingId) throws Exception {
        return mvc().perform(delete("/api/meetings/" + meetingId + "/participants/me")
                .header("Authorization", bearer(u)));
    }

    ResultActions cancel(User u, long meetingId) throws Exception {
        return mvc().perform(delete("/api/meetings/" + meetingId).header("Authorization", bearer(u)));
    }

    void setStatus(long meetingId, MeetingStatus status) {
        new TransactionTemplate(tx).execute(s -> {
            var m = meetings.findById(meetingId).orElseThrow();
            m.changeStatus(status, LocalDateTime.now(KST));
            return meetings.save(m);
        });
    }

    // ---------- 참여자 취소 ----------

    @Test
    void scheduledGuestCancelBeforeRefundDeadlineGetsSheepBack() throws Exception {
        User host = user(1), guest = user(2);
        long id = createMeeting(host, "SCHEDULED", 120);
        join(guest, id);
        assertThat(balance(guest)).isEqualTo(1);

        leave(guest, id).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.meetingId").value(id))
                .andExpect(jsonPath("$.data.refunded").value(true))
                .andExpect(jsonPath("$.data.sheepRefunded").value(1))
                .andExpect(jsonPath("$.data.sheepBalance").value(2))
                .andExpect(jsonPath("$.data.participantCount").value(1));
        assertThat(balance(guest)).isEqualTo(2);

        mvc().perform(get("/api/sheep/transactions?size=1").header("Authorization", bearer(guest)))
                .andExpect(jsonPath("$.data.items[0].type").value("MEETING_REFUND"))
                .andExpect(jsonPath("$.data.items[0].meetingId").value(id));
        // 상세에서는 신청 안 한 상태로, 내 모임·인원수에서도 빠진다
        mvc().perform(get("/api/meetings/" + id).header("Authorization", bearer(guest)))
                .andExpect(jsonPath("$.data.joined").value(false))
                .andExpect(jsonPath("$.data.participantCount").value(1));
        String mine = mvc().perform(get("/api/meetings/me").header("Authorization", bearer(guest)))
                .andReturn().getResponse().getContentAsString();
        assertThat((List<Integer>) JsonPath.read(mine, "$.data.meetings[*].meetingId")).doesNotContain((int) id);
    }

    @Test
    void scheduledGuestCancelInsideTenMinutesIsAllowedButNotRefunded() throws Exception {
        User host = user(1), guest = user(2);
        long id = createMeeting(host, "SCHEDULED", 8);
        join(guest, id);

        leave(guest, id).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.refunded").value(false))
                .andExpect(jsonPath("$.data.sheepRefunded").value(0))
                .andExpect(jsonPath("$.data.sheepBalance").value(1))
                .andExpect(jsonPath("$.data.participantCount").value(1));
        assertThat(balance(guest)).isEqualTo(1);
    }

    @Test
    void liveGuestLeavingBeforeStartGetsSheepBack() throws Exception {
        User host = user(1), guest = user(1);
        long id = createMeeting(host, "LIVE", null);
        join(guest, id);
        assertThat(balance(guest)).isZero();

        leave(guest, id).andExpect(status().isOk()).andExpect(jsonPath("$.data.refunded").value(true))
                .andExpect(jsonPath("$.data.sheepBalance").value(1));
        assertThat(participants.countActiveByMeetingId(id)).isEqualTo(1);
    }

    @Test
    void canceledGuestCannotJoinTheSameMeetingAgainAndIsNotChargedAgain() throws Exception {
        User host = user(1), guest = user(2);
        long id = createMeeting(host, "SCHEDULED", 120);
        join(guest, id);
        leave(guest, id).andExpect(status().isOk());
        assertThat(balance(guest)).isEqualTo(2);

        mvc().perform(post("/api/meetings/" + id + "/participants").header("Authorization", bearer(guest)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("ALREADY_CANCELED"));
        assertThat(balance(guest)).isEqualTo(2);
        // 같은 사람이 또 취소하면 참여자가 아니다
        leave(guest, id).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("NOT_PARTICIPANT"));
        assertThat(balance(guest)).isEqualTo(2);
    }

    @Test
    void canceledSeatIsFreedForOthers() throws Exception {
        User host = user(1), a = user(1), b = user(1);
        long id = createMeeting(host, "LIVE", null);
        join(a, id);
        leave(a, id).andExpect(status().isOk());
        join(b, id);
        assertThat(participants.countActiveByMeetingId(id)).isEqualTo(2);
    }

    @Test
    void leaveErrorsAreMapped() throws Exception {
        User host = user(1), guest = user(1), stranger = user(1);
        long id = createMeeting(host, "LIVE", null);
        join(guest, id);

        leave(host, id).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("HOST_CANNOT_LEAVE"));
        leave(stranger, id).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("NOT_PARTICIPANT"));
        leave(guest, 99999999L).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("MEETING_NOT_FOUND"));
        mvc().perform(delete("/api/meetings/" + id + "/participants/me")).andExpect(status().isUnauthorized());

        // 이미 시작했거나 끝난 모임은 취소할 수 없다 (양털 변화 없음)
        for (MeetingStatus s : List.of(MeetingStatus.IN_PROGRESS, MeetingStatus.ENDED, MeetingStatus.CANCELED)) {
            setStatus(id, s);
            leave(guest, id).andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error.code").value("MEETING_NOT_CANCELABLE"));
        }
        assertThat(balance(guest)).isZero();
    }

    // ---------- 방장 취소 ----------

    @Test
    void hostCancelOfScheduledMeetingRefundsEveryoneIncludingHost() throws Exception {
        User host = user(1), a = user(2), b = user(2);
        long id = createMeeting(host, "SCHEDULED", 120);
        join(a, id);
        join(b, id);
        assertThat(balance(host)).isZero();

        cancel(host, id).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.meetingId").value(id))
                .andExpect(jsonPath("$.data.status").value("CANCELED"))
                .andExpect(jsonPath("$.data.refundedParticipants").value(2))
                .andExpect(jsonPath("$.data.hostRefunded").value(true))
                .andExpect(jsonPath("$.data.sheepBalance").value(1));
        assertThat(balance(a)).isEqualTo(2);
        assertThat(balance(b)).isEqualTo(2);
        assertThat(balance(host)).isEqualTo(1);

        mvc().perform(get("/api/meetings/" + id).header("Authorization", bearer(a)))
                .andExpect(jsonPath("$.data.status").value("CANCELED"))
                .andExpect(jsonPath("$.data.cancelReason").value("HOST_CANCELED"));
        // 목록·내 모임에서 사라진다
        String list = mvc().perform(get("/api/meetings?type=SCHEDULED").header("Authorization", bearer(a)))
                .andReturn().getResponse().getContentAsString();
        assertThat((List<Integer>) JsonPath.read(list, "$.data.meetings[*].meetingId")).doesNotContain((int) id);
        String mine = mvc().perform(get("/api/meetings/me").header("Authorization", bearer(host)))
                .andReturn().getResponse().getContentAsString();
        assertThat((List<Integer>) JsonPath.read(mine, "$.data.meetings[*].meetingId")).doesNotContain((int) id);
    }

    @Test
    void hostCancelInsideTenMinutesRefundsGuestsButNotHost() throws Exception {
        User host = user(1), guest = user(1);
        long id = createMeeting(host, "SCHEDULED", 8);
        join(guest, id);

        cancel(host, id).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.refundedParticipants").value(1))
                .andExpect(jsonPath("$.data.hostRefunded").value(false))
                .andExpect(jsonPath("$.data.sheepBalance").value(0));
        assertThat(balance(guest)).isEqualTo(1);
        assertThat(balance(host)).isZero();
    }

    @Test
    void hostClosingLiveMeetingRefundsGuestsButNotHost() throws Exception {
        User host = user(1), guest = user(1);
        long id = createMeeting(host, "LIVE", null);
        join(guest, id);

        cancel(host, id).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.refundedParticipants").value(1))
                .andExpect(jsonPath("$.data.hostRefunded").value(false));
        assertThat(balance(guest)).isEqualTo(1);
        assertThat(balance(host)).isZero();
    }

    @Test
    void guestWhoAlreadyLeftIsNotRefundedTwice() throws Exception {
        User host = user(1), a = user(1), b = user(1);
        long id = createMeeting(host, "SCHEDULED", 120);
        join(a, id);
        join(b, id);
        leave(a, id).andExpect(status().isOk());
        assertThat(balance(a)).isEqualTo(1);

        cancel(host, id).andExpect(status().isOk()).andExpect(jsonPath("$.data.refundedParticipants").value(1));
        assertThat(balance(a)).isEqualTo(1);
        assertThat(balance(b)).isEqualTo(1);
    }

    @Test
    void hostCancelErrorsAreMapped() throws Exception {
        User host = user(1), guest = user(1);
        long id = createMeeting(host, "LIVE", null);
        join(guest, id);

        cancel(guest, id).andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
        cancel(host, 99999999L).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("MEETING_NOT_FOUND"));
        mvc().perform(delete("/api/meetings/" + id)).andExpect(status().isUnauthorized());

        cancel(host, id).andExpect(status().isOk());
        // 두 번째 취소는 거절, 환불도 한 번만
        cancel(host, id).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("MEETING_NOT_CANCELABLE"));
        assertThat(balance(guest)).isEqualTo(1);

        long started = createMeeting(user(1), "LIVE", null);
        long hostOfStarted = meetings.findById(started).orElseThrow().getHostUserId();
        setStatus(started, MeetingStatus.IN_PROGRESS);
        cancel(users.findById(hostOfStarted).orElseThrow(), started).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("MEETING_NOT_CANCELABLE"));
    }

    @Test
    void guestLeavingAndHostCancelingAtTheSameTimeRefundsTheGuestExactlyOnce() throws Exception {
        for (int round = 0; round < 3; round++) {
            User host = user(1), guest = user(1);
            long id = createMeeting(host, "SCHEDULED", 120);
            join(guest, id);
            assertThat(balance(guest)).isZero();

            ExecutorService pool = Executors.newFixedThreadPool(2);
            CountDownLatch go = new CountDownLatch(1);
            List<Future<Integer>> results = new ArrayList<>();
            results.add(pool.submit(() -> {
                go.await();
                return leave(guest, id).andReturn().getResponse().getStatus();
            }));
            results.add(pool.submit(() -> {
                go.await();
                return cancel(host, id).andReturn().getResponse().getStatus();
            }));
            go.countDown();
            int leaveCode = results.get(0).get();
            int cancelCode = results.get(1).get();
            pool.shutdown();

            // 방장 취소는 항상 성공하고, 나가기는 먼저면 200, 늦으면 409
            assertThat(cancelCode).isEqualTo(200);
            assertThat(leaveCode).isIn(200, 409);
            assertThat(balance(guest)).isEqualTo(1);
            assertThat(balance(host)).isEqualTo(1);
        }
    }
}
