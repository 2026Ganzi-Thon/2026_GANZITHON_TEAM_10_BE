package com.ganzithon.moyang.meeting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ganzithon.moyang.auth.JwtTokenProvider;
import com.ganzithon.moyang.common.port.Ports.MeetingInfo;
import com.ganzithon.moyang.common.port.Ports.MeetingMember;
import com.ganzithon.moyang.common.port.Ports.MeetingReader;
import com.ganzithon.moyang.common.port.Ports.MeetingSharePort;
import com.ganzithon.moyang.drawing.domain.Drawing;
import com.ganzithon.moyang.drawing.repository.DrawingRepository;
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
import java.util.List;
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
 * QA 수정: 예약 모임이 시작된 뒤에는 실제로 입장한 사람만 채팅 · 종료 동의 · 단계 진행 · 집계 · 회고 카드 · 공유 그림의 대상이다.
 * 신청만 하고 입장하지 않은 사람(여기서는 absent)이 있어도 입장한 사람끼리 정상적으로 진행하고 끝낼 수 있어야 한다.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:meeting-entry-only;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "moyang.upload-dir=build/test-uploads",
        "moyang.sheep.demo-daily-limit-off=true"
})
class MeetingEntryOnlyApiTest {

    static final AtomicInteger SEQ = new AtomicInteger();
    static final AtomicLong RECORD = new AtomicLong(1_300_000);
    static final ZoneId KST = ZoneId.of("Asia/Seoul");

    @Autowired WebApplicationContext context;
    @Autowired JwtTokenProvider tokens;
    @Autowired UserRepository users;
    @Autowired MeetingRepository meetings;
    @Autowired DrawingRepository drawings;
    @Autowired SheepService sheep;
    @Autowired PlatformTransactionManager tx;
    @Autowired JdbcTemplate jdbc;
    @Autowired MeetingLifecycleService lifecycle;
    @Autowired MeetingSharePort sharePort;
    @Autowired MeetingReader reader;

    MockMvc mvc() {
        return MockMvcBuilders.webAppContextSetup(context).build();
    }

    LocalDateTime now() {
        return LocalDateTime.now(KST);
    }

    User user() {
        User u = users.save(new User("eo-user-" + SEQ.incrementAndGet(), "x", "내 양"));
        new TransactionTemplate(tx).execute(s -> sheep.awardRecord(u.getId(), RECORD.incrementAndGet()));
        return u;
    }

    String bearer(User u) {
        return "Bearer " + tokens.issue(u.getId());
    }

    long createMeeting(User host, String type, Integer startInMinutes) throws Exception {
        String json = "{\"type\":\"" + type + "\",\"title\":\"입장자 테스트 " + SEQ.incrementAndGet()
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

    ResultActions enter(User u, long id) throws Exception {
        return mvc().perform(post("/api/meetings/" + id + "/enter").header("Authorization", bearer(u)));
    }

    ResultActions say(User u, long id, String text) throws Exception {
        return mvc().perform(post("/api/meetings/" + id + "/messages").header("Authorization", bearer(u))
                .contentType(MediaType.APPLICATION_JSON).content("{\"type\":\"TEXT\",\"content\":\"" + text + "\"}"));
    }

    ResultActions sendDrawing(User u, long id, Drawing d) throws Exception {
        return mvc().perform(post("/api/meetings/" + id + "/messages").header("Authorization", bearer(u))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"type\":\"DRAWING\",\"drawingId\":" + d.getId() + "}"));
    }

    ResultActions poll(User u, long id) throws Exception {
        return mvc().perform(get("/api/meetings/" + id + "/messages").header("Authorization", bearer(u)));
    }

    ResultActions next(User u, long id) throws Exception {
        return mvc().perform(post("/api/meetings/" + id + "/phase/next").header("Authorization", bearer(u)));
    }

    ResultActions end(User u, long id) throws Exception {
        return mvc().perform(post("/api/meetings/" + id + "/end").header("Authorization", bearer(u)));
    }

    Drawing drawingOf(User u) {
        return drawings.save(new Drawing(RECORD.incrementAndGet(), u.getId(), 1L, Drawing.InputType.UPLOAD,
                "내 그림", "eo-" + SEQ.incrementAndGet() + ".png", "image/png", 10));
    }

    void expectError(ResultActions r, int status, String code) throws Exception {
        r.andExpect(status().is(status)).andExpect(jsonPath("$.error.code").value(code));
    }

    /** 예약 모임: 방장 · a · absent가 신청하고 방장과 a만 입장해서 시작된 상태를 만든다. */
    long startedWithAnAbsentee(User host, User a, User absent) throws Exception {
        long id = createMeeting(host, "SCHEDULED", 120);
        join(a, id);
        join(absent, id);
        jdbc.update("update meetings set start_at = ? where id = ?", Timestamp.valueOf(now().plusMinutes(3)), id);
        enter(host, id).andExpect(status().isOk());
        enter(a, id).andExpect(status().isOk());
        jdbc.update("update meetings set start_at = ? where id = ?", Timestamp.valueOf(now().minusSeconds(10)), id);
        lifecycle.processDue(now());
        assertThat(meetings.findById(id).orElseThrow().getStatus().name()).isEqualTo("IN_PROGRESS");
        return id;
    }

    @Test
    void anAbsenteeCannotReadSendAgreeOrAdvanceAfterTheMeetingStarted() throws Exception {
        User host = user(), a = user(), absent = user();
        long id = startedWithAnAbsentee(host, a, absent);

        expectError(poll(absent, id), 403, "NOT_PARTICIPANT");
        expectError(say(absent, id, "저도 있어요"), 403, "NOT_PARTICIPANT");
        expectError(end(absent, id), 403, "NOT_JOINED");
        expectError(next(absent, id), 403, "NOT_PARTICIPANT");

        // 입장한 사람은 그대로 쓸 수 있다
        say(a, id, "안녕하세요").andExpect(status().isOk());
        poll(host, id).andExpect(status().isOk());
    }

    @Test
    void participantCountAndEndAgreementsOnlyCountThePeopleInTheRoom() throws Exception {
        User host = user(), a = user(), absent = user();
        long id = startedWithAnAbsentee(host, a, absent);

        poll(a, id).andExpect(jsonPath("$.data.participantCount").value(2));
        end(host, id).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.endAgreedCount").value(1))
                .andExpect(jsonPath("$.data.participantCount").value(2))
                .andExpect(jsonPath("$.data.status").value("IN_PROGRESS"));
        // 입장한 두 명이 모두 동의하면 입장하지 않은 사람이 있어도 끝난다
        end(a, id).andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("ENDED"));
        // 끝난 뒤에도 입장하지 않은 사람은 방 안을 볼 수 없다
        expectError(poll(absent, id), 403, "NOT_PARTICIPANT");
        expectError(end(absent, id), 403, "NOT_JOINED");
    }

    @Test
    void drawingPhaseAdvancesWhenEveryoneInTheRoomSubmittedEvenIfAnAbsenteeDidNot() throws Exception {
        User host = user(), a = user(), absent = user();
        long id = startedWithAnAbsentee(host, a, absent);
        next(host, id).andExpect(status().isOk()).andExpect(jsonPath("$.data.phase").value("DRAWING"));

        Drawing hostDrawing = drawingOf(host), aDrawing = drawingOf(a);
        sendDrawing(host, id, hostDrawing).andExpect(status().isOk());
        assertThat(meetings.findById(id).orElseThrow().getPhase().name()).isEqualTo("DRAWING");
        sendDrawing(a, id, aDrawing).andExpect(status().isOk());
        assertThat(meetings.findById(id).orElseThrow().getPhase().name()).isEqualTo("SHARING");
        poll(host, id).andExpect(jsonPath("$.data.submittedDrawingCount").value(2))
                .andExpect(jsonPath("$.data.participantCount").value(2));

        // 공유된 그림은 방 안에 있던 사람만 볼 수 있다
        assertThat(sharePort.isSharedWith(aDrawing.getId(), host.getId())).isTrue();
        assertThat(sharePort.isSharedWith(aDrawing.getId(), a.getId())).isTrue();
        assertThat(sharePort.isSharedWith(aDrawing.getId(), absent.getId())).isFalse();
    }

    @Test
    void reflectionCardsAreNotMadeForAnAbsentee() throws Exception {
        User host = user(), a = user(), absent = user();
        long id = startedWithAnAbsentee(host, a, absent);
        end(host, id).andExpect(status().isOk());
        end(a, id).andExpect(status().isOk());

        MeetingInfo info = reader.find(id).orElseThrow();
        assertThat(info.ended()).isTrue();
        assertThat(info.members()).extracting(MeetingMember::userId)
                .containsExactlyInAnyOrder(host.getId(), a.getId())
                .doesNotContain(absent.getId());
    }

    @Test
    void anAbsenteeCanStillSeeTheWaitingRoomAndTheCancelResultBeforeStart() throws Exception {
        User host = user(), a = user();
        long id = createMeeting(host, "SCHEDULED", 120);
        join(a, id);
        jdbc.update("update meetings set start_at = ? where id = ?", Timestamp.valueOf(now().plusMinutes(3)), id);

        // 시작 전에는 입장하지 않았어도 상태를 읽을 수 있다(시작·취소를 알아채야 하므로)
        poll(a, id).andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("OPEN"));

        // 자동 취소된 뒤에도 마찬가지
        jdbc.update("update meetings set start_at = ? where id = ?", Timestamp.valueOf(now().minusMinutes(3)), id);
        lifecycle.processDue(now());
        poll(a, id).andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("CANCELED"));
    }

    @Test
    void liveMeetingsStillCountEveryoneInTheWaitingRoom() throws Exception {
        User host = user(), guest = user();
        long id = createMeeting(host, "LIVE", null);
        join(guest, id);
        mvc().perform(post("/api/meetings/" + id + "/start").header("Authorization", bearer(host)))
                .andExpect(status().isOk());

        poll(guest, id).andExpect(status().isOk()).andExpect(jsonPath("$.data.participantCount").value(2));
        end(host, id).andExpect(status().isOk()).andExpect(jsonPath("$.data.participantCount").value(2));
        end(guest, id).andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("ENDED"));
    }
}
