package com.ganzithon.moyang.meeting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import com.ganzithon.moyang.user.domain.User;
import com.ganzithon.moyang.user.repository.UserRepository;
import com.jayway.jsonpath.JsonPath;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:meeting-query;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "moyang.upload-dir=build/test-uploads"
})
class MeetingQueryApiTest {

    static final AtomicInteger SEQ = new AtomicInteger();

    @Autowired WebApplicationContext context;
    @Autowired JwtTokenProvider tokens;
    @Autowired UserRepository users;
    @Autowired MeetingRepository meetings;
    @Autowired MeetingParticipantRepository participants;

    MockMvc mvc() {
        return MockMvcBuilders.webAppContextSetup(context).build();
    }

    LocalDateTime now() {
        return LocalDateTime.now(ZoneId.of("Asia/Seoul"));
    }

    User user(String themeCode) {
        User u = new User("mq-user-" + SEQ.incrementAndGet(), "x", "닉네임");
        u.changeThemeCode(themeCode);
        return users.save(u);
    }

    String bearer(User u) {
        return "Bearer " + tokens.issue(u.getId());
    }

    /** 방장을 참여자로 함께 저장한 모임을 만든다(만들기 API와 같은 모양). */
    Meeting meeting(MeetingType type, String category, LocalDateTime startAt, int capacity, User host,
                    MeetingStatus status) {
        Meeting m = new Meeting(type, "모임 " + SEQ.incrementAndGet(), null, category, startAt, capacity, host.getId(),
                "내가 서 있는 자리", now());
        if (status != MeetingStatus.RECRUITING && status != MeetingStatus.OPEN) {
            m.changeStatus(status, now());
        }
        m = meetings.save(m);
        participants.save(new MeetingParticipant(m.getId(), host.getId(), "방장 양", now()));
        return m;
    }

    void join(Meeting m, User u) {
        participants.save(new MeetingParticipant(m.getId(), u.getId(), "참여 양", now()));
    }

    /** 목록 JSON에서 meetingId가 id인 칸의 필드 값. */
    Object field(String body, Object id, String name) {
        List<?> found = JsonPath.read(body, "$.data.meetings[?(@.meetingId==" + id + ")]." + name);
        return found.get(0);
    }

    static String kst(LocalDateTime t) {
        return t.atZone(ZoneId.of("Asia/Seoul")).toOffsetDateTime().toString();
    }

    List<Integer> ids(String body) {
        return JsonPath.read(body, "$.data.meetings[*].meetingId");
    }

    String call(String url, User viewer) throws Exception {
        return mvc().perform(get(url).header("Authorization", bearer(viewer))).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    @Test
    void listShowsOnlyBeforeStartMeetingsOfTheTypeAndCountsHostAsParticipant() throws Exception {
        User host = user(null), viewer = user("RELATION_FAMILY");
        Meeting recruiting = meeting(MeetingType.SCHEDULED, "RELATION_FAMILY", now().plusHours(3), 6, host, MeetingStatus.RECRUITING);
        join(recruiting, user(null));
        Meeting inProgress = meeting(MeetingType.SCHEDULED, "RELATION_FAMILY", now().plusHours(1), 4, host, MeetingStatus.IN_PROGRESS);
        Meeting ended = meeting(MeetingType.SCHEDULED, "RELATION_FAMILY", now().plusHours(1), 4, host, MeetingStatus.ENDED);
        Meeting canceled = meeting(MeetingType.SCHEDULED, "RELATION_FAMILY", now().plusHours(1), 4, host, MeetingStatus.CANCELED);
        Meeting live = meeting(MeetingType.LIVE, "RELATION_FAMILY", null, 4, host, MeetingStatus.OPEN);

        String body = call("/api/meetings?type=SCHEDULED", viewer);
        assertThat(ids(body)).contains(recruiting.getId().intValue())
                .doesNotContain(inProgress.getId().intValue(), ended.getId().intValue(),
                        canceled.getId().intValue(), live.getId().intValue());
        assertThat(field(body, recruiting.getId(), "participantCount"))
                .isEqualTo(2);
        assertThat(field(body, recruiting.getId(), "status"))
                .isEqualTo("RECRUITING");
        assertThat(field(body, recruiting.getId(), "joined")).isEqualTo(false);

        String liveBody = call("/api/meetings?type=LIVE", viewer);
        assertThat(ids(liveBody)).contains(live.getId().intValue()).doesNotContain(recruiting.getId().intValue());
        assertThat(field(liveBody, live.getId(), "status"))
                .isEqualTo("OPEN");
        assertThat(field(liveBody, live.getId(), "startAt")).isNull();
    }

    @Test
    void joinedFlagCategoryFilterOrderAndRecommendation() throws Exception {
        User host = user(null), viewer = user("MONEY");
        Meeting later = meeting(MeetingType.SCHEDULED, "MONEY", now().plusHours(5), 6, host, MeetingStatus.RECRUITING);
        Meeting sooner = meeting(MeetingType.SCHEDULED, "MONEY", now().plusHours(2), 6, host, MeetingStatus.RECRUITING);
        Meeting other = meeting(MeetingType.SCHEDULED, "LOVE", now().plusHours(1), 6, host, MeetingStatus.RECRUITING);
        join(later, viewer);

        String body = call("/api/meetings?type=SCHEDULED&category=MONEY", viewer);
        List<Integer> ids = ids(body);
        assertThat(ids).contains(sooner.getId().intValue(), later.getId().intValue()).doesNotContain(other.getId().intValue());
        assertThat(ids.indexOf(sooner.getId().intValue())).isLessThan(ids.indexOf(later.getId().intValue()));
        assertThat(field(body, later.getId(), "joined")).isEqualTo(true);
        // 내 고민(MONEY)과 같은 카테고리 중 가장 빨리 시작하는 모임
        assertThat(((Number) JsonPath.read(body, "$.data.recommendedMeetingId")).longValue()).isEqualTo(sooner.getId());
        // startAt은 +09:00으로 내려간다
        assertThat(field(body, sooner.getId(), "startAt").toString())
                .endsWith("+09:00");

        String noTheme = call("/api/meetings?type=SCHEDULED", user(null));
        assertThat((Object) JsonPath.read(noTheme, "$.data.recommendedMeetingId")).isNull();
    }

    @Test
    void scheduledMeetingShowsAsOpenFromFiveMinutesBefore() throws Exception {
        User host = user(null);
        Meeting soon = meeting(MeetingType.SCHEDULED, "LOVE", now().plusMinutes(3), 4, host, MeetingStatus.RECRUITING);
        String body = call("/api/meetings?type=SCHEDULED&category=LOVE", host);
        assertThat(field(body, soon.getId(), "status"))
                .isEqualTo("OPEN");
    }

    @Test
    void listInputErrors() throws Exception {
        User viewer = user(null);
        mvc().perform(get("/api/meetings")).andExpect(status().isUnauthorized());
        mvc().perform(get("/api/meetings").header("Authorization", bearer(viewer)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
        mvc().perform(get("/api/meetings?type=WRONG").header("Authorization", bearer(viewer)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
        mvc().perform(get("/api/meetings?type=LIVE&category=NOPE").header("Authorization", bearer(viewer)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("INVALID_OPTION_CODE"));
    }

    @Test
    void detailForScheduledMeetingHidesTopicAndGivesTimes() throws Exception {
        User host = user(null), guest = user(null), stranger = user(null);
        LocalDateTime start = now().plusHours(4).withNano(0);
        Meeting m = meeting(MeetingType.SCHEDULED, "RELATION_FAMILY", start, 6, host, MeetingStatus.RECRUITING);
        join(m, guest);

        mvc().perform(get("/api/meetings/" + m.getId()).header("Authorization", bearer(guest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("RECRUITING"))
                .andExpect(jsonPath("$.data.participantCount").value(2))
                .andExpect(jsonPath("$.data.capacity").value(6))
                .andExpect(jsonPath("$.data.joined").value(true))
                .andExpect(jsonPath("$.data.myMeetingNickname").value("참여 양"))
                .andExpect(jsonPath("$.data.isHost").value(false))
                .andExpect(jsonPath("$.data.hostUserId").value(host.getId()))
                .andExpect(jsonPath("$.data.canStart").value(false))
                .andExpect(jsonPath("$.data.canSkip").value(false))
                .andExpect(jsonPath("$.data.topic").doesNotExist())
                .andExpect(jsonPath("$.data.steps.length()").value(4))
                .andExpect(jsonPath("$.data.tags[0]").value("인간관계·가족"))
                .andExpect(jsonPath("$.data.cancelReason").doesNotExist());

        String body = call("/api/meetings/" + m.getId(), stranger);
        assertThat((Boolean) JsonPath.read(body, "$.data.joined")).isFalse();
        assertThat((Object) JsonPath.read(body, "$.data.myMeetingNickname")).isNull();
        assertThat((String) JsonPath.read(body, "$.data.enterOpenAt")).isEqualTo(kst(start.minusMinutes(5)));
        assertThat((String) JsonPath.read(body, "$.data.cancelRefundUntil")).isEqualTo(kst(start.minusMinutes(10)));
        assertThat((String) JsonPath.read(body, "$.data.endAt")).isEqualTo(kst(start.plusMinutes(40)));
    }

    @Test
    void liveDetailShowsTopicAndHostCanStartOnlyWithTwoOrMore() throws Exception {
        User host = user(null), guest = user(null);
        Meeting m = meeting(MeetingType.LIVE, "LOVE", null, 4, host, MeetingStatus.OPEN);

        mvc().perform(get("/api/meetings/" + m.getId()).header("Authorization", bearer(host)))
                .andExpect(jsonPath("$.data.isHost").value(true))
                .andExpect(jsonPath("$.data.canStart").value(false))
                .andExpect(jsonPath("$.data.topic").value("내가 서 있는 자리"))
                .andExpect(jsonPath("$.data.enterOpenAt").doesNotExist());

        join(m, guest);
        mvc().perform(get("/api/meetings/" + m.getId()).header("Authorization", bearer(host)))
                .andExpect(jsonPath("$.data.canStart").value(true));
        mvc().perform(get("/api/meetings/" + m.getId()).header("Authorization", bearer(guest)))
                .andExpect(jsonPath("$.data.canStart").value(false));
    }

    @Test
    void canceledDetailHasReasonAndMissingOrExpiredIs404() throws Exception {
        User host = user(null);
        Meeting canceled = meeting(MeetingType.SCHEDULED, "LOVE", now().plusHours(2), 4, host, MeetingStatus.RECRUITING);
        canceled.cancel(CancelReason.NOT_ENOUGH_PARTICIPANTS, now());
        meetings.save(canceled);
        mvc().perform(get("/api/meetings/" + canceled.getId()).header("Authorization", bearer(host)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELED"))
                .andExpect(jsonPath("$.data.cancelReason").value("NOT_ENOUGH_PARTICIPANTS"));

        mvc().perform(get("/api/meetings/99999999").header("Authorization", bearer(host)))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.error.code").value("MEETING_NOT_FOUND"));

        Meeting old = meeting(MeetingType.LIVE, "LOVE", null, 4, host, MeetingStatus.OPEN);
        old.changeStatus(MeetingStatus.ENDED, now().minusHours(25));
        meetings.save(old);
        mvc().perform(get("/api/meetings/" + old.getId()).header("Authorization", bearer(host)))
                .andExpect(status().isNotFound());
    }

    @Test
    void myMeetingsIncludeHostedAndJoinedButNotEndedOrCanceled() throws Exception {
        User me = user(null), other = user(null);
        Meeting hosted = meeting(MeetingType.LIVE, "LOVE", null, 4, me, MeetingStatus.OPEN);
        Meeting joinedLater = meeting(MeetingType.SCHEDULED, "LOVE", now().plusHours(6), 4, other, MeetingStatus.RECRUITING);
        Meeting joinedSooner = meeting(MeetingType.SCHEDULED, "LOVE", now().plusHours(1), 4, other, MeetingStatus.RECRUITING);
        Meeting running = meeting(MeetingType.SCHEDULED, "LOVE", now().minusMinutes(5), 4, other, MeetingStatus.IN_PROGRESS);
        Meeting ended = meeting(MeetingType.SCHEDULED, "LOVE", now().minusHours(2), 4, other, MeetingStatus.ENDED);
        Meeting notMine = meeting(MeetingType.LIVE, "LOVE", null, 4, other, MeetingStatus.OPEN);
        for (Meeting m : List.of(joinedLater, joinedSooner, running, ended)) {
            join(m, me);
        }

        String body = call("/api/meetings/me", me);
        List<Integer> ids = ids(body);
        assertThat(ids).contains(hosted.getId().intValue(), joinedLater.getId().intValue(),
                joinedSooner.getId().intValue(), running.getId().intValue())
                .doesNotContain(ended.getId().intValue(), notMine.getId().intValue());
        assertThat(ids.indexOf(joinedSooner.getId().intValue())).isLessThan(ids.indexOf(joinedLater.getId().intValue()));
        assertThat(field(body, hosted.getId(), "isHost")).isEqualTo(true);
        assertThat(field(body, joinedLater.getId(), "myMeetingNickname"))
                .isEqualTo("참여 양");
        assertThat(field(body, running.getId(), "status"))
                .isEqualTo("IN_PROGRESS");
    }
}
