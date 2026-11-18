package com.ganzithon.moyang.meeting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ganzithon.moyang.auth.JwtTokenProvider;
import com.ganzithon.moyang.common.port.Ports.MeetingInfo;
import com.ganzithon.moyang.common.port.Ports.MeetingMember;
import com.ganzithon.moyang.drawing.domain.Drawing;
import com.ganzithon.moyang.drawing.repository.DrawingRepository;
import com.ganzithon.moyang.meeting.repository.MeetingMessageRepository;
import com.ganzithon.moyang.meeting.repository.MeetingRepository;
import com.ganzithon.moyang.meeting.service.MeetingPortService;
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

/** 모임 채팅 보내기 · 폴링과 모임 포트(그림 공유 권한 · 회고 카드 재료)의 진짜 구현. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:meeting-chat;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "moyang.upload-dir=build/test-uploads",
        "moyang.sheep.demo-daily-limit-off=true"
})
class MeetingChatApiTest {

    static final AtomicInteger SEQ = new AtomicInteger();
    static final AtomicLong RECORD = new AtomicLong(800_000);
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
    @Autowired MeetingPortService ports;

    MockMvc mvc() {
        return MockMvcBuilders.webAppContextSetup(context).build();
    }

    User user() {
        User u = users.save(new User("mc-user-" + SEQ.incrementAndGet(), "x", "내 양"));
        for (int i = 0; i < 3; i++) {
            new TransactionTemplate(tx).execute(s -> sheep.awardRecord(u.getId(), RECORD.incrementAndGet()));
        }
        return u;
    }

    String bearer(User u) {
        return "Bearer " + tokens.issue(u.getId());
    }

    /** 방장 닉네임 "방장양"으로 모임을 만든다. startInMinutes가 null이면 실시간. */
    long createMeeting(User host, Integer startInMinutes) throws Exception {
        String json = "{\"type\":\"" + (startInMinutes == null ? "LIVE" : "SCHEDULED") + "\",\"title\":\"채팅 테스트 "
                + SEQ.incrementAndGet() + "\",\"category\":\"RELATION_FAMILY\",\"capacity\":6,"
                + "\"meetingNickname\":\"방장양\""
                + (startInMinutes == null ? "" : ",\"startAt\":\""
                + OffsetDateTime.now(KST).plusMinutes(startInMinutes).withNano(0) + "\"") + "}";
        String res = mvc().perform(post("/api/meetings").header("Authorization", bearer(host))
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(res, "$.data.meetingId")).longValue();
    }

    void join(User u, long id, String nickname) throws Exception {
        mvc().perform(post("/api/meetings/" + id + "/participants").header("Authorization", bearer(u))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"meetingNickname\":\"" + nickname + "\"}"))
                .andExpect(status().isOk());
    }

    /** 실시간 모임을 만들고 손님 한 명을 넣어 시작한다. 방장 "방장양", 손님 "손님양". */
    long startedLive(User host, User guest) throws Exception {
        long id = createMeeting(host, null);
        join(guest, id, "손님양");
        mvc().perform(post("/api/meetings/" + id + "/start").header("Authorization", bearer(host)))
                .andExpect(status().isOk());
        return id;
    }

    /** 시작한 실시간 모임을 방장이 그림 단계(DRAWING)로 넘긴다. 그림은 이 단계부터 올릴 수 있다. */
    long drawingPhaseLive(User host, User guest) throws Exception {
        long id = startedLive(host, guest);
        mvc().perform(post("/api/meetings/" + id + "/phase/next").header("Authorization", bearer(host)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.phase").value("DRAWING"));
        return id;
    }

    /** 지금까지 쌓인 가장 마지막 메시지 번호(시작·단계 안내 같은 시스템 메시지를 건너뛰고 폴링하려고). */
    long lastMessageId(long id) {
        List<com.ganzithon.moyang.meeting.domain.MeetingMessage> all = messages.findByMeetingIdOrderByIdAsc(id);
        return all.get(all.size() - 1).getId();
    }

    ResultActions send(User u, long id, String json) throws Exception {
        return mvc().perform(post("/api/meetings/" + id + "/messages").header("Authorization", bearer(u))
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    ResultActions sendText(User u, long id, String content) throws Exception {
        return send(u, id, "{\"type\":\"TEXT\",\"content\":\"" + content + "\"}");
    }

    long sendTextId(User u, long id, String content) throws Exception {
        String res = sendText(u, id, content).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(res, "$.data.messageId")).longValue();
    }

    ResultActions poll(User u, long id, String query) throws Exception {
        return mvc().perform(get("/api/meetings/" + id + "/messages" + query).header("Authorization", bearer(u)));
    }

    ResultActions poll(User u, long id) throws Exception {
        return poll(u, id, "");
    }

    Drawing drawingOf(User u) {
        return drawings.save(new Drawing(RECORD.incrementAndGet(), u.getId(), 1L, Drawing.InputType.UPLOAD,
                "내 그림", "mc-" + SEQ.incrementAndGet() + ".png", "image/png", 10));
    }

    void expectError(ResultActions r, int status, String code) throws Exception {
        r.andExpect(status().is(status)).andExpect(jsonPath("$.error.code").value(code));
    }

    void setStatus(long id, String status, LocalDateTime endedAt) {
        jdbc.update("update meetings set status = ?, ended_at = ? where id = ?", status,
                endedAt == null ? null : Timestamp.valueOf(endedAt), id);
    }

    // ---- 보내기 · 폴링 ----

    @Test
    void textMessageIsSeenByTheOtherParticipantWithRoomInfo() throws Exception {
        User host = user();
        User guest = user();
        long id = startedLive(host, guest);

        sendText(host, id, "저는 오늘 조금 지쳐서 왔어요")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.messageId").isNumber())
                .andExpect(jsonPath("$.data.createdAt").isString());

        poll(guest, id).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.data.phase").value("INTRO"))
                .andExpect(jsonPath("$.data.currentStep").value(1))
                .andExpect(jsonPath("$.data.topic").isString())
                .andExpect(jsonPath("$.data.participantCount").value(2))
                .andExpect(jsonPath("$.data.submittedDrawingCount").value(0))
                .andExpect(jsonPath("$.data.endAgreedCount").value(0))
                .andExpect(jsonPath("$.data.endAt").doesNotExist())
                .andExpect(jsonPath("$.data.deleteAt").doesNotExist())
                // 시작 안내(SYSTEM) 한 줄 + 방장 메시지
                .andExpect(jsonPath("$.data.messages.length()").value(2))
                .andExpect(jsonPath("$.data.messages[0].type").value("SYSTEM"))
                .andExpect(jsonPath("$.data.messages[0].senderNickname").doesNotExist())
                .andExpect(jsonPath("$.data.messages[0].mine").value(false))
                .andExpect(jsonPath("$.data.messages[1].type").value("TEXT"))
                .andExpect(jsonPath("$.data.messages[1].senderNickname").value("방장양"))
                .andExpect(jsonPath("$.data.messages[1].content").value("저는 오늘 조금 지쳐서 왔어요"))
                .andExpect(jsonPath("$.data.messages[1].mine").value(false))
                .andExpect(jsonPath("$.data.messages[1].imageUrl").doesNotExist())
                // 응답에 userId는 없다
                .andExpect(jsonPath("$.data.messages[1].userId").doesNotExist());

        poll(host, id).andExpect(jsonPath("$.data.messages[1].mine").value(true));
    }

    @Test
    void afterIdReturnsOnlyNewerMessagesInOrder() throws Exception {
        User host = user();
        User guest = user();
        long id = startedLive(host, guest);
        long first = sendTextId(host, id, "하나");
        long second = sendTextId(guest, id, "둘");
        long third = sendTextId(host, id, "셋");
        assertThat(first).isLessThan(second);
        assertThat(second).isLessThan(third);

        poll(guest, id).andExpect(jsonPath("$.data.messages.length()").value(4)) // 시작 안내 + 3개
                .andExpect(jsonPath("$.data.messages[0].type").value("SYSTEM"))
                .andExpect(jsonPath("$.data.messages[1].content").value("하나"))
                .andExpect(jsonPath("$.data.messages[3].content").value("셋"));
        poll(guest, id, "?afterId=" + first).andExpect(jsonPath("$.data.messages.length()").value(2))
                .andExpect(jsonPath("$.data.messages[0].content").value("둘"));
        poll(guest, id, "?afterId=" + third).andExpect(jsonPath("$.data.messages").isEmpty())
                // 새 메시지가 없어도 진행 정보는 온다
                .andExpect(jsonPath("$.data.status").value("IN_PROGRESS"));
        poll(guest, id, "?afterId=-5").andExpect(jsonPath("$.data.messages.length()").value(4));
        expectError(poll(guest, id, "?afterId=abc"), 400, "INVALID_REQUEST");
    }

    @Test
    void sharedDrawingShowsImageUrlAndCountsEachSenderOnce() throws Exception {
        User host = user();
        User guest = user();
        long id = drawingPhaseLive(host, guest);
        long after = lastMessageId(id);
        Drawing drawing = drawingOf(guest);

        send(guest, id, "{\"type\":\"DRAWING\",\"drawingId\":" + drawing.getId()
                + ",\"content\":\"창문이 하나뿐인 방을 그렸어요\"}").andExpect(status().isOk());
        // 같은 사람이 한 장 더 올려도 제출한 사람 수는 1
        send(guest, id, "{\"type\":\"DRAWING\",\"drawingId\":" + drawingOf(guest).getId() + "}")
                .andExpect(status().isOk());

        poll(host, id, "?afterId=" + after).andExpect(jsonPath("$.data.submittedDrawingCount").value(1))
                .andExpect(jsonPath("$.data.phase").value("DRAWING"))
                .andExpect(jsonPath("$.data.messages[0].type").value("DRAWING"))
                .andExpect(jsonPath("$.data.messages[0].senderNickname").value("손님양"))
                .andExpect(jsonPath("$.data.messages[0].content").value("창문이 하나뿐인 방을 그렸어요"))
                .andExpect(jsonPath("$.data.messages[0].imageUrl").value("/api/drawings/" + drawing.getId() + "/image"))
                .andExpect(jsonPath("$.data.messages[1].content").doesNotExist());

        send(host, id, "{\"type\":\"DRAWING\",\"drawingId\":" + drawingOf(host).getId() + "}")
                .andExpect(status().isOk());
        poll(host, id).andExpect(jsonPath("$.data.submittedDrawingCount").value(2));
    }

    @Test
    void drawingErrors() throws Exception {
        User host = user();
        User guest = user();
        long id = drawingPhaseLive(host, guest);
        long after = lastMessageId(id);

        expectError(send(guest, id, "{\"type\":\"DRAWING\",\"drawingId\":" + drawingOf(host).getId() + "}"),
                403, "FORBIDDEN");
        expectError(send(guest, id, "{\"type\":\"DRAWING\",\"drawingId\":99999999}"), 404, "DRAWING_NOT_FOUND");
        expectError(send(guest, id, "{\"type\":\"DRAWING\"}"), 400, "INVALID_REQUEST");
        poll(guest, id, "?afterId=" + after).andExpect(jsonPath("$.data.messages").isEmpty());
    }

    @Test
    void textValidation() throws Exception {
        User host = user();
        User guest = user();
        long id = startedLive(host, guest);

        for (String json : new String[] {"{\"type\":\"SYSTEM\",\"content\":\"가짜 안내\"}", "{\"content\":\"타입 없음\"}",
                "{\"type\":\"IMAGE\",\"content\":\"x\"}", "{\"type\":\"text\",\"content\":\"소문자\"}"}) {
            send(host, id, json).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("INVALID_MESSAGE_TYPE"))
                    .andExpect(jsonPath("$.error.message").value("type은 TEXT, DRAWING 중 하나여야 해요."));
        }
        for (String json : new String[] {"{\"type\":\"TEXT\"}", "{\"type\":\"TEXT\",\"content\":null}",
                "{\"type\":\"TEXT\",\"content\":\"   \"}"}) {
            send(host, id, json).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("EMPTY_CONTENT"))
                    .andExpect(jsonPath("$.error.message").value("메시지 내용을 입력해 주세요."));
        }
        sendText(host, id, "가".repeat(500)).andExpect(status().isOk());
        sendText(host, id, "🐑".repeat(500)).andExpect(status().isOk());
        sendText(host, id, "가".repeat(501)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("CONTENT_TOO_LONG"))
                .andExpect(jsonPath("$.error.message").value("메시지는 500자까지 쓸 수 있어요."));
        send(host, id, "not json").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));

        sendText(host, id, "  앞뒤 공백  ").andExpect(status().isOk());
        poll(guest, id, "").andExpect(jsonPath("$.data.messages[3].content").value("앞뒤 공백"));
    }

    // ---- 권한 · 상태 ----

    @Test
    void onlyActiveParticipantsCanSendOrRead() throws Exception {
        User host = user();
        User guest = user();
        User outsider = user();
        long id = startedLive(host, guest);

        expectError(sendText(outsider, id, "들어가도 되나요"), 403, "NOT_PARTICIPANT");
        expectError(poll(outsider, id), 403, "NOT_PARTICIPANT");
        expectError(sendText(host, 987_654_321L, "없는 모임"), 404, "MEETING_NOT_FOUND");
        expectError(poll(host, 987_654_321L), 404, "MEETING_NOT_FOUND");
    }

    @Test
    void participantWhoCanceledBeforeStartIsNotAParticipant() throws Exception {
        User host = user();
        User stayer = user();
        User leaver = user();
        long id = createMeeting(host, null);
        join(stayer, id, "남은양");
        join(leaver, id, "나간양");
        mvc().perform(delete("/api/meetings/" + id + "/participants/me").header("Authorization", bearer(leaver)))
                .andExpect(status().isOk());
        mvc().perform(post("/api/meetings/" + id + "/start").header("Authorization", bearer(host)))
                .andExpect(status().isOk());

        expectError(sendText(leaver, id, "나갔는데요"), 403, "NOT_PARTICIPANT");
        expectError(poll(leaver, id), 403, "NOT_PARTICIPANT");
        poll(stayer, id).andExpect(jsonPath("$.data.participantCount").value(2));
    }

    @Test
    void requiresLogin() throws Exception {
        mvc().perform(post("/api/meetings/1/messages").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"TEXT\",\"content\":\"hi\"}"))
                .andExpect(status().isUnauthorized());
        mvc().perform(get("/api/meetings/1/messages")).andExpect(status().isUnauthorized());
    }

    @Test
    void waitingRoomCanPollButNotSend() throws Exception {
        User host = user();
        User guest = user();
        long id = createMeeting(host, null);
        join(guest, id, "손님양");

        expectError(sendText(host, id, "아직이에요"), 409, "MEETING_NOT_IN_PROGRESS");
        // 대기실에서도 폴링은 된다(프론트가 시작을 알아채야 하므로). 시작 전에는 phase · currentStep이 없다.
        poll(guest, id).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("OPEN"))
                .andExpect(jsonPath("$.data.phase").doesNotExist())
                .andExpect(jsonPath("$.data.currentStep").doesNotExist())
                .andExpect(jsonPath("$.data.participantCount").value(2))
                .andExpect(jsonPath("$.data.messages").isEmpty());

        mvc().perform(post("/api/meetings/" + id + "/start").header("Authorization", bearer(host)))
                .andExpect(status().isOk());
        poll(guest, id).andExpect(jsonPath("$.data.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.data.phase").value("INTRO"));
    }

    @Test
    void canceledMeetingShowsCanceledToWaitingParticipants() throws Exception {
        User host = user();
        User guest = user();
        long id = createMeeting(host, null);
        join(guest, id, "손님양");
        mvc().perform(delete("/api/meetings/" + id).header("Authorization", bearer(host)))
                .andExpect(status().isOk());

        poll(guest, id).andExpect(jsonPath("$.data.status").value("CANCELED"));
        expectError(sendText(host, id, "취소됐어요"), 409, "MEETING_NOT_IN_PROGRESS");
    }

    @Test
    void endedMeetingIsReadableForTwentyFourHoursThenGone() throws Exception {
        User host = user();
        User guest = user();
        long id = startedLive(host, guest);
        sendText(host, id, "마지막 인사").andExpect(status().isOk());

        LocalDateTime endedAt = LocalDateTime.now(KST).minusHours(1);
        setStatus(id, "ENDED", endedAt);
        expectError(sendText(host, id, "끝난 뒤에는 못 보내요"), 409, "MEETING_NOT_IN_PROGRESS");
        poll(guest, id).andExpect(jsonPath("$.data.status").value("ENDED"))
                .andExpect(jsonPath("$.data.messages.length()").value(2)) // 시작 안내 + 마지막 인사
                .andExpect(jsonPath("$.data.deleteAt").value(
                        org.hamcrest.Matchers.startsWith(endedAt.plusHours(24).toLocalDate().toString())));

        setStatus(id, "ENDED", LocalDateTime.now(KST).minusHours(25));
        expectError(poll(guest, id), 404, "MEETING_NOT_FOUND");
        expectError(sendText(host, id, "방이 사라졌어요"), 404, "MEETING_NOT_FOUND");
    }

    @Test
    void scheduledMeetingHidesTopicUntilStartedAndShowsEndAt() throws Exception {
        User host = user();
        User guest = user();
        long id = createMeeting(host, 3);
        join(guest, id, "손님양");

        poll(guest, id).andExpect(jsonPath("$.data.status").value("OPEN"))
                .andExpect(jsonPath("$.data.topic").doesNotExist())
                .andExpect(jsonPath("$.data.endAt").isString());

        jdbc.update("update meetings set status = 'IN_PROGRESS', phase = 'INTRO' where id = ?", id);
        poll(guest, id).andExpect(jsonPath("$.data.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.data.topic").isString());
        sendText(guest, id, "예약 모임 채팅").andExpect(status().isOk());
    }

    // ---- 순서 보장 ----

    @Test
    void concurrentSendsAreAllStoredInIdOrder() throws Exception {
        User host = user();
        User guest = user();
        long id = startedLive(host, guest);
        int perUser = 8;
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();
        for (User sender : new User[] {host, guest}) {
            futures.add(pool.submit(() -> {
                go.await();
                for (int i = 0; i < perUser; i++) {
                    sendText(sender, id, "동시 메시지 " + i).andExpect(status().isOk());
                }
                return null;
            }));
        }
        go.countDown();
        for (Future<?> f : futures) {
            f.get();
        }
        pool.shutdown();

        List<Long> ids = messages.findByMeetingIdOrderByIdAsc(id).stream().map(m -> m.getId()).toList();
        assertThat(ids).hasSize(perUser * 2 + 1).isSorted(); // + 시작 안내
    }

    // ---- 포트 진짜 구현 ----

    @Test
    void sharedDrawingIsViewableOnlyByParticipantsOfThatMeeting() throws Exception {
        User host = user();
        User guest = user();
        User outsider = user();
        long id = drawingPhaseLive(host, guest);
        Drawing drawing = drawingOf(guest);
        Drawing unshared = drawingOf(guest);

        assertThat(ports.isSharedWith(drawing.getId(), host.getId())).isFalse(); // 아직 공유 전
        send(guest, id, "{\"type\":\"DRAWING\",\"drawingId\":" + drawing.getId() + "}").andExpect(status().isOk());

        assertThat(ports.isSharedWith(drawing.getId(), host.getId())).isTrue();
        assertThat(ports.isSharedWith(drawing.getId(), outsider.getId())).isFalse();
        assertThat(ports.isSharedWith(unshared.getId(), host.getId())).isFalse();

        // 종료 후 24시간이 지나 채팅이 삭제되면 더는 볼 수 없다
        setStatus(id, "ENDED", LocalDateTime.now(KST).minusHours(1));
        assertThat(ports.isSharedWith(drawing.getId(), host.getId())).isTrue();
        setStatus(id, "ENDED", LocalDateTime.now(KST).minusHours(25));
        assertThat(ports.isSharedWith(drawing.getId(), host.getId())).isFalse();
    }

    @Test
    void canceledParticipantCannotViewSharedDrawing() throws Exception {
        User host = user();
        User guest = user();
        User leaver = user();
        long id = createMeeting(host, null);
        join(guest, id, "손님양");
        join(leaver, id, "나간양");
        mvc().perform(delete("/api/meetings/" + id + "/participants/me").header("Authorization", bearer(leaver)))
                .andExpect(status().isOk());
        mvc().perform(post("/api/meetings/" + id + "/start").header("Authorization", bearer(host)))
                .andExpect(status().isOk());
        mvc().perform(post("/api/meetings/" + id + "/phase/next").header("Authorization", bearer(host)))
                .andExpect(status().isOk());
        Drawing drawing = drawingOf(guest);
        send(guest, id, "{\"type\":\"DRAWING\",\"drawingId\":" + drawing.getId() + "}").andExpect(status().isOk());

        assertThat(ports.isSharedWith(drawing.getId(), host.getId())).isTrue();
        assertThat(ports.isSharedWith(drawing.getId(), leaver.getId())).isFalse();
    }

    @Test
    void readerGivesCardMaterialPerParticipant() throws Exception {
        User host = user();
        User guest = user();
        long id = drawingPhaseLive(host, guest);
        Drawing first = drawingOf(guest);
        Drawing latest = drawingOf(guest);
        sendText(host, id, "방장 첫마디").andExpect(status().isOk());
        sendText(guest, id, "손님 첫마디").andExpect(status().isOk());
        send(guest, id, "{\"type\":\"DRAWING\",\"drawingId\":" + first.getId() + "}").andExpect(status().isOk());
        send(guest, id, "{\"type\":\"DRAWING\",\"drawingId\":" + latest.getId() + ",\"content\":\"그림 설명\"}")
                .andExpect(status().isOk());
        sendText(guest, id, "손님 마지막").andExpect(status().isOk());
        sendText(host, id, "방장 마지막").andExpect(status().isOk());

        MeetingInfo running = ports.find(id).orElseThrow();
        assertThat(running.ended()).isFalse();
        assertThat(running.members()).hasSize(2);

        setStatus(id, "ENDED", LocalDateTime.now(KST));
        MeetingInfo ended = ports.find(id).orElseThrow();
        assertThat(ended.ended()).isTrue();
        MeetingMember hostMember = ended.members().stream().filter(m -> m.userId() == host.getId()).findFirst().orElseThrow();
        MeetingMember guestMember = ended.members().stream().filter(m -> m.userId() == guest.getId()).findFirst().orElseThrow();
        // 본인이 쓴 텍스트만, 쓴 순서대로. 다른 사람 발언과 그림 설명은 들어가지 않는다
        assertThat(hostMember.messages()).containsExactly("방장 첫마디", "방장 마지막");
        assertThat(hostMember.drawingId()).isNull();
        assertThat(guestMember.messages()).containsExactly("손님 첫마디", "손님 마지막");
        // 그림은 가장 최근에 공유한 한 장
        assertThat(guestMember.drawingId()).isEqualTo(latest.getId());
    }

    @Test
    void readerExcludesCanceledParticipantsAndUnknownMeeting() throws Exception {
        User host = user();
        User guest = user();
        User leaver = user();
        long id = createMeeting(host, null);
        join(guest, id, "손님양");
        join(leaver, id, "나간양");
        mvc().perform(delete("/api/meetings/" + id + "/participants/me").header("Authorization", bearer(leaver)))
                .andExpect(status().isOk());

        assertThat(ports.find(id).orElseThrow().members()).extracting(MeetingMember::userId)
                .containsExactlyInAnyOrder(host.getId(), guest.getId());
        assertThat(ports.find(987_654_321L)).isEmpty();
    }
}
