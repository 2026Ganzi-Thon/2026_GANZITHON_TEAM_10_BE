package com.ganzithon.moyang.meeting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ganzithon.moyang.auth.JwtTokenProvider;
import com.ganzithon.moyang.meeting.domain.Meeting;
import com.ganzithon.moyang.meeting.domain.MeetingEnums.MeetingStatus;
import com.ganzithon.moyang.meeting.domain.MeetingEnums.MeetingType;
import com.ganzithon.moyang.meeting.domain.MeetingParticipant;
import com.ganzithon.moyang.meeting.repository.MeetingParticipantRepository;
import com.ganzithon.moyang.meeting.repository.MeetingRepository;
import com.ganzithon.moyang.sheep.service.SheepService;
import com.ganzithon.moyang.user.domain.User;
import com.ganzithon.moyang.user.repository.UserRepository;
import java.time.LocalDateTime;
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

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:meeting-join;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "moyang.upload-dir=build/test-uploads",
        "moyang.sheep.demo-daily-limit-off=true"
})
class MeetingJoinApiTest {

    static final AtomicInteger SEQ = new AtomicInteger();
    static final AtomicLong RECORD = new AtomicLong(800_000);

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

    LocalDateTime now() {
        return LocalDateTime.now(ZoneId.of("Asia/Seoul"));
    }

    User user(int sheepCount) {
        User u = users.save(new User("mj-user-" + SEQ.incrementAndGet(), "x", "내 양"));
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

    /** 방장이 이미 참여자로 들어 있는 모임. */
    Meeting meeting(MeetingType type, LocalDateTime startAt, int capacity, MeetingStatus status) {
        User host = user(0);
        Meeting m = new Meeting(type, "신청 테스트 " + SEQ.incrementAndGet(), null, "LOVE", startAt, capacity,
                host.getId(), "주제", now());
        if (status != MeetingStatus.RECRUITING && status != MeetingStatus.OPEN) {
            m.changeStatus(status, now());
        }
        m = meetings.save(m);
        participants.save(new MeetingParticipant(m.getId(), host.getId(), "방장 양", now()));
        return m;
    }

    ResultActions join(User u, Meeting m, String json) throws Exception {
        var req = post("/api/meetings/" + m.getId() + "/participants").header("Authorization", bearer(u));
        if (json != null) {
            req = req.contentType(MediaType.APPLICATION_JSON).content(json);
        }
        return mvc().perform(req);
    }

    @Test
    void joiningScheduledMeetingCostsOneSheepAndShowsCount() throws Exception {
        Meeting m = meeting(MeetingType.SCHEDULED, now().plusHours(5), 6, MeetingStatus.RECRUITING);
        User guest = user(3);
        join(guest, m, "{\"meetingNickname\":\"딸기 양\"}").andExpect(status().isOk())
                .andExpect(jsonPath("$.data.meetingId").value(m.getId()))
                .andExpect(jsonPath("$.data.participantCount").value(2))
                .andExpect(jsonPath("$.data.capacity").value(6))
                .andExpect(jsonPath("$.data.status").value("RECRUITING"))
                .andExpect(jsonPath("$.data.meetingNickname").value("딸기 양"))
                .andExpect(jsonPath("$.data.sheepUsed").value(1))
                .andExpect(jsonPath("$.data.sheepBalance").value(2));
        assertThat(balance(guest)).isEqualTo(2);
        mvc().perform(get("/api/sheep/transactions?size=1").header("Authorization", bearer(guest)))
                .andExpect(jsonPath("$.data.items[0].type").value("MEETING_JOIN"))
                .andExpect(jsonPath("$.data.items[0].meetingId").value(m.getId()));
        // 상세에서 신청 완료로 보이고 내 모임에 들어간다
        mvc().perform(get("/api/meetings/" + m.getId()).header("Authorization", bearer(guest)))
                .andExpect(jsonPath("$.data.joined").value(true))
                .andExpect(jsonPath("$.data.myMeetingNickname").value("딸기 양"))
                .andExpect(jsonPath("$.data.participantCount").value(2));
    }

    @Test
    void liveJoinWorksWithoutBodyAndUsesDefaultNickname() throws Exception {
        Meeting m = meeting(MeetingType.LIVE, null, 4, MeetingStatus.OPEN);
        User guest = user(1);
        join(guest, m, null).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("OPEN"))
                .andExpect(jsonPath("$.data.meetingNickname").value("내 양"))
                .andExpect(jsonPath("$.data.sheepBalance").value(0));
    }

    @Test
    void errorsAreMappedAndNothingIsChargedOnFailure() throws Exception {
        Meeting m = meeting(MeetingType.SCHEDULED, now().plusHours(5), 2, MeetingStatus.RECRUITING);
        User first = user(2), second = user(2), poor = user(0);

        // 닉네임 오류는 먼저 걸러진다
        join(first, m, "{\"meetingNickname\":\"양\"}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_NICKNAME"));
        assertThat(balance(first)).isEqualTo(2);

        join(first, m, null).andExpect(status().isOk());
        // 이미 신청함 (양털 추가 차감 없음)
        join(first, m, null).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("ALREADY_JOINED"));
        assertThat(balance(first)).isEqualTo(1);
        // 정원 2명(방장+first)이 다 참
        join(second, m, null).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("MEETING_FULL"));
        assertThat(balance(second)).isEqualTo(2);

        // 양털 부족이면 참여도 저장되지 않는다
        Meeting open = meeting(MeetingType.LIVE, null, 4, MeetingStatus.OPEN);
        long before = participants.countActiveByMeetingId(open.getId());
        join(poor, open, null).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("NOT_ENOUGH_WOOL"));
        assertThat(participants.countActiveByMeetingId(open.getId())).isEqualTo(before);

        mvc().perform(post("/api/meetings/99999999/participants").header("Authorization", bearer(first)))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.error.code").value("MEETING_NOT_FOUND"));
        mvc().perform(post("/api/meetings/" + m.getId() + "/participants")).andExpect(status().isUnauthorized());
    }

    @Test
    void hostCannotJoinAgainAndStartedOrFinishedMeetingsRejectNewcomers() throws Exception {
        Meeting hosted = meeting(MeetingType.LIVE, null, 4, MeetingStatus.OPEN);
        User host = users.findById(hosted.getHostUserId()).orElseThrow();
        join(host, hosted, null).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("ALREADY_JOINED"));

        User guest = user(5);
        for (MeetingStatus s : List.of(MeetingStatus.IN_PROGRESS, MeetingStatus.ENDED, MeetingStatus.CANCELED)) {
            Meeting m = meeting(MeetingType.LIVE, null, 4, s);
            join(guest, m, null).andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error.code").value("MEETING_NOT_JOINABLE"));
        }
        // 예약 시각이 지났는데 아직 시작 처리 전인 모임도 신규 입장 불가
        Meeting late = meeting(MeetingType.SCHEDULED, now().minusMinutes(1), 4, MeetingStatus.RECRUITING);
        join(guest, late, null).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("MEETING_NOT_JOINABLE"));
        assertThat(balance(guest)).isEqualTo(5);
    }

    @Test
    void simultaneousJoinsNeverExceedCapacityAndLosersKeepTheirSheep() throws Exception {
        Meeting m = meeting(MeetingType.LIVE, null, 4, MeetingStatus.OPEN); // 방장 1명 + 자리 3개
        List<User> guests = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            guests.add(user(1));
        }
        ExecutorService pool = Executors.newFixedThreadPool(8);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<Integer>> results = new ArrayList<>();
        for (User g : guests) {
            results.add(pool.submit(() -> {
                go.await();
                return join(g, m, null).andReturn().getResponse().getStatus();
            }));
        }
        go.countDown();
        int ok = 0, full = 0;
        for (Future<Integer> f : results) {
            int code = f.get();
            if (code == 200) {
                ok++;
            } else if (code == 409) {
                full++;
            }
        }
        pool.shutdown();

        assertThat(ok).isEqualTo(3);
        assertThat(full).isEqualTo(5);
        assertThat(participants.countActiveByMeetingId(m.getId())).isEqualTo(4);
        // 들어간 3명만 양털 1개를 썼다
        long spent = guests.stream().filter(g -> balance(g) == 0).count();
        assertThat(spent).isEqualTo(3);
        assertThat(guests.stream().filter(g -> balance(g) == 1).count()).isEqualTo(5);
    }

    @Test
    void sameUserDoubleClickChargesOnlyOnce() throws Exception {
        Meeting m = meeting(MeetingType.LIVE, null, 4, MeetingStatus.OPEN);
        User guest = user(2);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<Integer>> results = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            results.add(pool.submit(() -> {
                go.await();
                return join(guest, m, null).andReturn().getResponse().getStatus();
            }));
        }
        go.countDown();
        List<Integer> codes = List.of(results.get(0).get(), results.get(1).get());
        pool.shutdown();
        assertThat(codes).containsExactlyInAnyOrder(200, 409);
        assertThat(balance(guest)).isEqualTo(1);
        assertThat(participants.countActiveByMeetingId(m.getId())).isEqualTo(2);
    }
}
