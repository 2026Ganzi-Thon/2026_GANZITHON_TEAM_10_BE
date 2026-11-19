package com.ganzithon.moyang.meeting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ganzithon.moyang.auth.JwtTokenProvider;
import com.ganzithon.moyang.meeting.repository.MeetingMessageRepository;
import com.ganzithon.moyang.meeting.repository.MeetingParticipantRepository;
import com.ganzithon.moyang.meeting.repository.MeetingRepository;
import com.ganzithon.moyang.meeting.service.MeetingPurgeService;
import com.ganzithon.moyang.sheep.service.SheepService;
import com.ganzithon.moyang.user.domain.User;
import com.ganzithon.moyang.user.repository.UserRepository;
import com.jayway.jsonpath.JsonPath;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.ZoneId;
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

/** QA 수정: 모임이 끝나고 24시간이 지나면 채팅 원문을 DB에서 지운다(모임 · 참여자는 남는다). */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:meeting-purge;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "moyang.upload-dir=build/test-uploads",
        "moyang.sheep.demo-daily-limit-off=true"
})
class MeetingPurgeApiTest {

    static final AtomicInteger SEQ = new AtomicInteger();
    static final AtomicLong RECORD = new AtomicLong(1_500_000);
    static final ZoneId KST = ZoneId.of("Asia/Seoul");

    @Autowired WebApplicationContext context;
    @Autowired JwtTokenProvider tokens;
    @Autowired UserRepository users;
    @Autowired MeetingRepository meetings;
    @Autowired MeetingParticipantRepository participants;
    @Autowired MeetingMessageRepository messages;
    @Autowired SheepService sheep;
    @Autowired PlatformTransactionManager tx;
    @Autowired JdbcTemplate jdbc;
    @Autowired MeetingPurgeService purge;

    MockMvc mvc() {
        return MockMvcBuilders.webAppContextSetup(context).build();
    }

    User user() {
        User u = users.save(new User("pg-user-" + SEQ.incrementAndGet(), "x", "내 양"));
        new TransactionTemplate(tx).execute(s -> sheep.awardRecord(u.getId(), RECORD.incrementAndGet()));
        return u;
    }

    String bearer(User u) {
        return "Bearer " + tokens.issue(u.getId());
    }

    ResultActions call(User u, String path, String body) throws Exception {
        var req = post(path).header("Authorization", bearer(u));
        return mvc().perform(body == null ? req : req.contentType(MediaType.APPLICATION_JSON).content(body));
    }

    /** 실시간 모임을 만들어 시작하고, 서로 이야기한 뒤 둘 다 종료에 동의해서 끝낸다. */
    long endedMeeting(User host, User guest) throws Exception {
        String res = call(host, "/api/meetings", "{\"type\":\"LIVE\",\"title\":\"삭제 테스트 " + SEQ.incrementAndGet()
                + "\",\"category\":\"RELATION_FAMILY\",\"capacity\":4}")
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        long id = ((Number) JsonPath.read(res, "$.data.meetingId")).longValue();
        call(guest, "/api/meetings/" + id + "/participants", null).andExpect(status().isOk());
        call(host, "/api/meetings/" + id + "/start", null).andExpect(status().isOk());
        call(host, "/api/meetings/" + id + "/messages", "{\"type\":\"TEXT\",\"content\":\"안녕하세요\"}")
                .andExpect(status().isOk());
        call(guest, "/api/meetings/" + id + "/messages", "{\"type\":\"TEXT\",\"content\":\"반가워요\"}")
                .andExpect(status().isOk());
        call(host, "/api/meetings/" + id + "/end", null).andExpect(status().isOk());
        call(guest, "/api/meetings/" + id + "/end", null).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ENDED"));
        return id;
    }

    void endedHoursAgo(long id, long hours) {
        jdbc.update("update meetings set ended_at = ? where id = ?",
                Timestamp.valueOf(LocalDateTime.now(KST).minusHours(hours)), id);
    }

    @Test
    void messagesOfMeetingsEndedMoreThan24HoursAgoAreDeletedFromTheDb() throws Exception {
        long old = endedMeeting(user(), user());
        assertThat(messages.findByMeetingIdOrderByIdAsc(old)).isNotEmpty();
        endedHoursAgo(old, 25);

        int deleted = purge.purgeExpired(LocalDateTime.now(KST));

        assertThat(deleted).isGreaterThanOrEqualTo(3); // 안내 메시지 + 대화 2개 + 종료 안내
        assertThat(messages.findByMeetingIdOrderByIdAsc(old)).isEmpty();
        // 조회도 여전히 404
        User host = user();
        mvc().perform(get("/api/meetings/" + old + "/messages").header("Authorization", bearer(host)))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void theMeetingAndItsParticipantsStayAfterThePurge() throws Exception {
        long id = endedMeeting(user(), user());
        endedHoursAgo(id, 30);

        purge.purgeExpired(LocalDateTime.now(KST));

        assertThat(meetings.findById(id)).isPresent();
        assertThat(participants.countActiveByMeetingId(id)).isEqualTo(2);
    }

    @Test
    void recentlyEndedAndRunningMeetingsKeepTheirMessages() throws Exception {
        long recent = endedMeeting(user(), user());
        endedHoursAgo(recent, 23);

        User host = user(), guest = user();
        String res = call(host, "/api/meetings", "{\"type\":\"LIVE\",\"title\":\"진행 중\","
                + "\"category\":\"RELATION_FAMILY\",\"capacity\":2}")
                .andReturn().getResponse().getContentAsString();
        long running = ((Number) JsonPath.read(res, "$.data.meetingId")).longValue();
        call(guest, "/api/meetings/" + running + "/participants", null).andExpect(status().isOk());
        call(host, "/api/meetings/" + running + "/start", null).andExpect(status().isOk());
        call(host, "/api/meetings/" + running + "/messages", "{\"type\":\"TEXT\",\"content\":\"아직 진행 중\"}")
                .andExpect(status().isOk());

        int recentBefore = messages.findByMeetingIdOrderByIdAsc(recent).size();
        int runningBefore = messages.findByMeetingIdOrderByIdAsc(running).size();
        purge.purgeExpired(LocalDateTime.now(KST));

        assertThat(messages.findByMeetingIdOrderByIdAsc(recent)).hasSize(recentBefore);
        assertThat(messages.findByMeetingIdOrderByIdAsc(running)).hasSize(runningBefore);
    }

    @Test
    void runningThePurgeAgainDeletesNothingMore() throws Exception {
        long id = endedMeeting(user(), user());
        endedHoursAgo(id, 26);

        assertThat(purge.purgeExpired(LocalDateTime.now(KST))).isGreaterThan(0);
        assertThat(purge.purgeExpired(LocalDateTime.now(KST))).isZero();
    }

    @Test
    void otherMeetingsAreNotTouchedWhenOneExpires() throws Exception {
        long expired = endedMeeting(user(), user());
        long kept = endedMeeting(user(), user());
        endedHoursAgo(expired, 25);
        endedHoursAgo(kept, 2);
        int keptBefore = messages.findByMeetingIdOrderByIdAsc(kept).size();

        purge.purgeExpired(LocalDateTime.now(KST));

        assertThat(messages.findByMeetingIdOrderByIdAsc(expired)).isEmpty();
        assertThat(messages.findByMeetingIdOrderByIdAsc(kept)).hasSize(keptBefore);
    }
}
