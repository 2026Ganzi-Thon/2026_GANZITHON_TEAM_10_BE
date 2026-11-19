package com.ganzithon.moyang.meeting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ganzithon.moyang.auth.JwtTokenProvider;
import com.ganzithon.moyang.meeting.repository.MeetingParticipantRepository;
import com.ganzithon.moyang.meeting.repository.MeetingRepository;
import com.ganzithon.moyang.sheep.service.SheepService;
import com.ganzithon.moyang.user.domain.User;
import com.ganzithon.moyang.user.repository.UserRepository;
import com.jayway.jsonpath.JsonPath;
import java.time.OffsetDateTime;
import java.time.ZoneId;
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
        "spring.datasource.url=jdbc:h2:mem:meeting-create;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "moyang.upload-dir=build/test-uploads",
        "moyang.sheep.demo-daily-limit-off=true"   // 같은 날 기록을 여러 번 저장해 양털을 여러 개 채우기 위해
})
class MeetingCreateApiTest {

    static final AtomicInteger SEQ = new AtomicInteger();
    static final AtomicLong RECORD = new AtomicLong(700_000);

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

    /** 양털을 sheepCount개 가진 사용자. */
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

    String inHours(int hours) {
        return OffsetDateTime.now(ZoneId.of("Asia/Seoul")).plusHours(hours).withNano(0).toString();
    }

    ResultActions create(User u, String json) throws Exception {
        return mvc().perform(post("/api/meetings").header("Authorization", bearer(u))
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    static String body(String type, String title, String startAt, Object capacity, String nickname) {
        return "{\"type\":\"" + type + "\",\"title\":\"" + title + "\",\"category\":\"RELATION_FAMILY\""
                + (startAt == null ? "" : ",\"startAt\":\"" + startAt + "\"")
                + (capacity == null ? "" : ",\"capacity\":" + capacity)
                + (nickname == null ? "" : ",\"meetingNickname\":\"" + nickname + "\"") + "}";
    }

    @Test
    void liveMeetingOpensImmediatelyAndCostsOneSheepAndHostIsParticipant() throws Exception {
        User host = user(2);
        String res = create(host, body("LIVE", "지금 모여요", null, 4, "딸기 양"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("OPEN"))
                .andExpect(jsonPath("$.data.participantCount").value(1))
                .andExpect(jsonPath("$.data.sheepUsed").value(1))
                .andExpect(jsonPath("$.data.sheepBalance").value(1))
                .andReturn().getResponse().getContentAsString();
        long id = ((Number) JsonPath.read(res, "$.data.meetingId")).longValue();

        assertThat(balance(host)).isEqualTo(1);
        var hostRow = participants.findByMeetingIdAndUserId(id, host.getId()).orElseThrow();
        assertThat(hostRow.getMeetingNickname()).isEqualTo("딸기 양");

        // 양털 내역에 MEETING_CREATE가 모임 번호와 함께 남는다
        mvc().perform(get("/api/sheep/transactions?size=1").header("Authorization", bearer(host)))
                .andExpect(jsonPath("$.data.items[0].type").value("MEETING_CREATE"))
                .andExpect(jsonPath("$.data.items[0].meetingId").value(id));
        // 상세 · 내 모임에 바로 보인다 (x/정원에 방장 포함)
        mvc().perform(get("/api/meetings/" + id).header("Authorization", bearer(host)))
                .andExpect(jsonPath("$.data.isHost").value(true))
                .andExpect(jsonPath("$.data.joined").value(true))
                .andExpect(jsonPath("$.data.participantCount").value(1))
                .andExpect(jsonPath("$.data.topic").value("우리 사이의 거리"));
        String mine = mvc().perform(get("/api/meetings/me").header("Authorization", bearer(host)))
                .andReturn().getResponse().getContentAsString();
        assertThat((java.util.List<Integer>) JsonPath.read(mine, "$.data.meetings[*].meetingId"))
                .contains((int) id);
    }

    @Test
    void scheduledMeetingStartsRecruitingWithDefaultNickname() throws Exception {
        User host = user(1);
        String res = create(host, body("SCHEDULED", "내일 모여요", inHours(26), 6, null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("RECRUITING"))
                .andExpect(jsonPath("$.data.sheepBalance").value(0))
                .andReturn().getResponse().getContentAsString();
        long id = ((Number) JsonPath.read(res, "$.data.meetingId")).longValue();
        // 모임 닉네임을 안 보내면 내 양 닉네임
        assertThat(participants.findByMeetingIdAndUserId(id, host.getId()).orElseThrow().getMeetingNickname())
                .isEqualTo("내 양");
        String list = mvc().perform(get("/api/meetings?type=SCHEDULED&category=RELATION_FAMILY")
                .header("Authorization", bearer(host))).andReturn().getResponse().getContentAsString();
        assertThat((java.util.List<Integer>) JsonPath.read(list, "$.data.meetings[*].meetingId")).contains((int) id);
    }

    @Test
    void validationErrorsUseSpecCodesAndCostNothing() throws Exception {
        User host = user(1);
        long before = meetings.count();
        String title31 = "가".repeat(31);

        create(host, body("LIVE", "정원 오류", null, 3, null)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_CAPACITY"));
        create(host, body("SCHEDULED", "시각 없음", null, 4, null)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("START_AT_REQUIRED"));
        create(host, body("SCHEDULED", "지난 시각", inHours(-1), 4, null)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
        create(host, body("SCHEDULED", "너무 먼 시각", inHours(24 * 8), 4, null)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
        create(host, body("LIVE", title31, null, 4, null)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
        create(host, body("LIVE", " ", null, 4, null)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
        create(host, body("LIVE", "정원 없음", null, null, null)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
        create(host, body("WRONG", "종류 오류", null, 4, null)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
        create(host, body("LIVE", "닉네임 짧음", null, 4, "양")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_NICKNAME"));
        create(host, "{\"type\":\"LIVE\",\"title\":\"코드 오류\",\"category\":\"NOPE\",\"capacity\":4}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("INVALID_OPTION_CODE"));
        create(host, "not json").andExpect(status().isBadRequest());

        assertThat(meetings.count()).isEqualTo(before);
        assertThat(balance(host)).isEqualTo(1);
    }

    @Test
    void notEnoughSheepRollsBackTheWholeCreation() throws Exception {
        User poor = user(0);
        long meetingsBefore = meetings.count();
        long participantsBefore = participants.count();
        create(poor, body("LIVE", "양털 없음", null, 4, null)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("NOT_ENOUGH_WOOL"));
        assertThat(meetings.count()).isEqualTo(meetingsBefore);
        assertThat(participants.count()).isEqualTo(participantsBefore);
        assertThat(balance(poor)).isZero();
    }

    @Test
    void needsLogin() throws Exception {
        mvc().perform(post("/api/meetings").contentType(MediaType.APPLICATION_JSON)
                .content(body("LIVE", "로그인 없음", null, 4, null))).andExpect(status().isUnauthorized());
    }
}
