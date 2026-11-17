package com.ganzithon.moyang;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.ganzithon.moyang.ai.client.AiClient;
import com.ganzithon.moyang.common.exception.BusinessException;
import com.ganzithon.moyang.common.exception.ErrorCode;
import com.ganzithon.moyang.common.port.Ports.CurrentUserProvider;
import com.ganzithon.moyang.ai.service.MeetingCardService;
import com.ganzithon.moyang.common.port.Ports.MeetingInfo;
import com.ganzithon.moyang.common.port.Ports.MeetingMember;
import com.ganzithon.moyang.common.port.Ports.MeetingReader;
import com.ganzithon.moyang.common.port.Ports.MeetingSharePort;
import com.ganzithon.moyang.common.port.Ports.RecordPort;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import com.ganzithon.moyang.common.port.Ports.TopicInfo;
import com.ganzithon.moyang.common.port.Ports.TopicReader;
import com.ganzithon.moyang.common.port.Ports.UserContext;
import com.ganzithon.moyang.common.port.Ports.UserContextProvider;
import com.ganzithon.moyang.common.port.Ports.WoolPort;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/** Drawing · AI 흐름 검증. 실제 Upstage 키 없이 가짜 AI로 돈다. 아래 Fakes가 PortFallbacks(@Fallback)보다 우선한다. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:moyang;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "moyang.upload-dir=build/test-uploads"
})
class ConversationFlowTest {

    static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};
    static final String CRISIS_TEXT = "요즘은 그냥 죽고 싶다는 생각이 들어요";

    static long userId;
    static int woolBalance;
    static final Set<String> woolSpent = new HashSet<>();
    static boolean shared;
    static boolean aiFails;
    static boolean aiSaysNeedsSupport;
    static String lastCardPrompt;
    static MeetingInfo meeting;
    static final AtomicLong nextRecordId = new AtomicLong(); // 테스트끼리 DB를 공유하므로 초기화하지 않는다
    static final Set<Long> cardReadyRecords = new HashSet<>();

    @TestConfiguration
    static class Fakes {
        @Bean
        CurrentUserProvider currentUserProvider() {
            return () -> userId;
        }

        @Bean
        RecordPort recordPort() {
            return new RecordPort() {
                public long create(long id) {
                    return nextRecordId.incrementAndGet();
                }

                public void cardReady(long recordId) {
                    cardReadyRecords.add(recordId);
                }
            };
        }

        @Bean
        UserContextProvider userContextProvider() {
            return id -> UserContext.EMPTY;
        }

        @Bean
        TopicReader topicReader() {
            return id -> id == 3 ? Optional.of(new TopicInfo(3, "내가 서 있는 자리", "설명")) : Optional.empty();
        }

        @Bean
        WoolPort woolPort() {
            return new WoolPort() {
                public int balance(long id) {
                    return woolBalance;
                }

                public void spend(long id, String referenceId) {
                    if (woolBalance < 1) {
                        throw new BusinessException(ErrorCode.NOT_ENOUGH_WOOL);
                    }
                    if (woolSpent.add(referenceId)) {
                        woolBalance--;
                    }
                }
            };
        }

        @Bean
        MeetingSharePort meetingSharePort() {
            return (drawingId, id) -> shared;
        }

        @Bean
        MeetingReader meetingReader() {
            return id -> Optional.ofNullable(meeting);
        }

        @Bean
        @Primary
        AiClient fakeAi() {
            return (system, user, schemaName, schema) -> {
                if (aiFails) {
                    throw new IllegalStateException("ai down");
                }
                if (schemaName.equals("card")) {
                    lastCardPrompt = user;
                    return "{\"whatISaid\":\"그림 이야기\",\"thoughts\":\"떠오른 생각\",\"todaysStory\":\"오늘의 마음\"}";
                }
                return "{\"question\":\"어떤 마음이었나요?\",\"needsSupport\":" + aiSaysNeedsSupport
                        + ",\"aspect\":\"DRAWING\"}";
            };
        }
    }

    @Autowired
    WebApplicationContext context;
    @Autowired
    MeetingCardService meetingCards;
    MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
        userId = 1;
        woolBalance = 0;
        woolSpent.clear();
        shared = false;
        aiFails = false;
        aiSaysNeedsSupport = false;
        lastCardPrompt = null;
        meeting = null;
    }

    @Test
    void 모임_회고_카드는_본인_발언만으로_참여자별로_만든다() throws Exception {
        uploadOk("창문이 하나뿐인 방");
        long drawingId = lastDrawingId;
        mvc.perform(get("/api/meetings/30/reflection")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("MEETING_NOT_FOUND"));

        List<MeetingMember> members = List.of(
                new MeetingMember(1, drawingId, List.of("조용한 곳이 필요했어요", CRISIS_TEXT)),
                new MeetingMember(2, null, List.of("저는 그림을 못 냈어요")));
        meeting = new MeetingInfo(false, members);
        mvc.perform(get("/api/meetings/30/reflection")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("MEETING_NOT_ENDED"));

        meeting = new MeetingInfo(true, members);
        meetingCards.generateMeetingCards(30);
        meetingCards.generateMeetingCards(30); // 두 번 불려도 한 장
        assertThat(lastCardPrompt).contains("조용한 곳이 필요했어요").doesNotContain("죽고 싶다")
                .doesNotContain("그림을 못 냈어요");

        mvc.perform(get("/api/meetings/30/reflection")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.meetingId").value(30))
                .andExpect(jsonPath("$.data.drawing.drawingId").value(drawingId))
                .andExpect(jsonPath("$.data.card.whatISaid").value("그림 이야기"));
        userId = 2;
        mvc.perform(get("/api/meetings/30/reflection")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NO_DRAWING_IN_MEETING"));
        userId = 3;
        mvc.perform(get("/api/meetings/30/reflection")).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("NOT_PARTICIPANT"));
    }

    @Test
    void 개인_흐름_업로드부터_양털_연장과_회고_카드까지() throws Exception {
        woolBalance = 2;
        long recordId = number(uploadOk("창가에 서 있는 나"), "$.data.recordId");
        long drawingId = lastDrawingId;
        long conversationId = start(drawingId);

        for (int i = 1; i <= 4; i++) {
            answer(conversationId, "답 " + i)
                    .andExpect(jsonPath("$.data.question.order").value(i + 1))
                    .andExpect(jsonPath("$.data.remainingFreeQuestions").value(4 - i))
                    .andExpect(jsonPath("$.data.woolUsed").value(0));
        }
        // 기본 질문 5개를 다 쓰면 질문 없이 연장 가능 여부만 알려준다
        answer(conversationId, "답 5")
                .andExpect(jsonPath("$.data.question").isEmpty())
                .andExpect(jsonPath("$.data.isLastQuestion").value(true))
                .andExpect(jsonPath("$.data.canExtend").value(true));
        mvc.perform(get("/api/conversations/" + conversationId))
                .andExpect(jsonPath("$.data.canExtend").value(true))
                .andExpect(jsonPath("$.data.card").isEmpty());

        // 이미 답했고 남은 질문이 없으면 답을 또 받지 않는다
        answer(conversationId, "답 5 한 번 더").andExpect(status().isBadRequest());

        // 답은 이미 보냈으니 useWool만 보내서 6번째 질문을 받는다
        send(conversationId, "{\"useWool\":true}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.question.order").value(6))
                .andExpect(jsonPath("$.data.woolUsed").value(1));
        assertThat(woolBalance).isEqualTo(1);

        end(conversationId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ENDED"))
                .andExpect(jsonPath("$.data.recordId").value(recordId))
                .andExpect(jsonPath("$.data.drawing.imageUrl").value("/api/drawings/" + drawingId + "/image"))
                .andExpect(jsonPath("$.data.drawing.topicTitle").value("내가 서 있는 자리"))
                .andExpect(jsonPath("$.data.card.whatISaid").value("그림 이야기"))
                .andExpect(jsonPath("$.data.fallback").value(false));
        assertThat(cardReadyRecords).contains(recordId);

        mvc.perform(get("/api/conversations/" + conversationId))
                .andExpect(jsonPath("$.data.status").value("ENDED"))
                .andExpect(jsonPath("$.data.card.todaysStory").value("오늘의 마음"))
                .andExpect(jsonPath("$.data.messages.length()").value(11));
        end(conversationId).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("CONVERSATION_ALREADY_ENDED"));
        answer(conversationId, "더 말하기").andExpect(status().isConflict());
    }

    @Test
    void 남의_그림과_대화는_볼_수_없다() throws Exception {
        uploadOk(null);
        long drawingId = lastDrawingId;
        long conversationId = start(drawingId);

        userId = 2;
        mvc.perform(get("/api/drawings/" + drawingId)).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
        mvc.perform(get("/api/drawings/" + drawingId + "/image")).andExpect(status().isForbidden());
        mvc.perform(get("/api/conversations/" + conversationId)).andExpect(status().isForbidden());
        answer(conversationId, "남의 대화").andExpect(status().isForbidden());
        end(conversationId).andExpect(status().isForbidden());
        mvc.perform(post("/api/conversations").contentType(MediaType.APPLICATION_JSON)
                .content("{\"drawingId\":" + drawingId + "}")).andExpect(status().isForbidden());

        // 모임에 공유된 그림은 이미지만 볼 수 있다 (정보 조회는 여전히 주인만)
        shared = true;
        mvc.perform(get("/api/drawings/" + drawingId + "/image")).andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/png"));
        mvc.perform(get("/api/drawings/" + drawingId)).andExpect(status().isForbidden());

        userId = 1;
        mvc.perform(get("/api/drawings/" + drawingId)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.conversationId").value(conversationId));
        mvc.perform(get("/api/drawings/999999")).andExpect(status().isNotFound());
    }

    @Test
    void 업로드_검증() throws Exception {
        upload(new MockMultipartFile("image", "a.png", "image/png", "not an image".getBytes()), "PHOTO", null, null)
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("INVALID_IMAGE_FORMAT"));
        upload(png(), "PHOTO", null, "가".repeat(201))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("NOTE_TOO_LONG"));
        upload(png(), "SCAN", null, null)
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("INVALID_INPUT_TYPE"));
        upload(png(), null, null, null)
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
        upload(null, "PHOTO", null, null)
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
        upload(png(), "PHOTO", "999", null)
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.error.code").value("TOPIC_NOT_FOUND"));
        upload(new MockMultipartFile("image", "big.png", "image/png", bigPng()), "PHOTO", null, null)
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("IMAGE_TOO_LARGE"));
    }

    @Test
    void 대화_요청_검증() throws Exception {
        uploadOk(null);
        long conversationId = start(lastDrawingId);
        mvc.perform(post("/api/conversations").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"drawingId\":" + lastDrawingId + "}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("CONVERSATION_ALREADY_EXISTS"));
        send(conversationId, "{\"type\":\"HELLO\"}").andExpect(jsonPath("$.error.code").value("INVALID_MESSAGE_TYPE"));
        send(conversationId, "{\"type\":\"ANSWER\",\"content\":\"   \"}")
                .andExpect(jsonPath("$.error.code").value("EMPTY_CONTENT"));
        send(conversationId, "{\"type\":\"ANSWER\",\"content\":\"" + "가".repeat(1001) + "\"}")
                .andExpect(jsonPath("$.error.code").value("CONTENT_TOO_LONG"));
        // 질문이 아직 남아 있는데 useWool만 보내면 안 된다
        send(conversationId, "{\"useWool\":true}").andExpect(status().isBadRequest());
        send(conversationId, "{\"type\":\"DONT_KNOW\"}").andExpect(jsonPath("$.data.question.order").value(2));
        send(conversationId, "{\"type\":\"SKIP\"}").andExpect(jsonPath("$.data.question.order").value(3));
        mvc.perform(put("/api/conversations/" + conversationId).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"DONE\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("INVALID_STATUS"));
        mvc.perform(get("/api/conversations/999999")).andExpect(status().isNotFound());
    }

    @Test
    void 위기_표현은_질문_대신_안내가_나가고_카드_재료에서_빠진다() throws Exception {
        woolBalance = 3;
        uploadOk(null);
        long conversationId = start(lastDrawingId);

        answer(conversationId, CRISIS_TEXT)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.question").isEmpty())
                .andExpect(jsonPath("$.data.supportNotice.phone").value("109"))
                .andExpect(jsonPath("$.data.isLastQuestion").value(false))
                .andExpect(jsonPath("$.data.remainingFreeQuestions").value(4));
        // 입력창은 열려 있고, 다시 답하면 대화가 이어진다
        answer(conversationId, "창밖을 보는 사람을 그렸어요")
                .andExpect(jsonPath("$.data.question.order").value(2))
                .andExpect(jsonPath("$.data.supportNotice").isEmpty());

        // 키워드에 안 걸려도 AI가 감지하면 안내가 나간다
        aiSaysNeedsSupport = true;
        answer(conversationId, "이제 다 그만하고 싶어요")
                .andExpect(jsonPath("$.data.question").isEmpty())
                .andExpect(jsonPath("$.data.supportNotice.phone").value("109"));
        aiSaysNeedsSupport = false;

        end(conversationId).andExpect(status().isOk());
        assertThat(lastCardPrompt).contains("창밖을 보는 사람").doesNotContain("죽고 싶다").doesNotContain("그만하고 싶어요")
                .doesNotContain("어떤 마음이었나요?"); // AI 질문은 카드 재료에 넣지 않는다
        assertThat(woolBalance).isEqualTo(3);
    }

    /** 시연 영상 · 부스용 수위 낮은 문장. AI 판단 없이 키워드만으로 109 안내가 떠야 한다. */
    @Test
    void 시연용_위기_문장은_키워드만으로_안내가_뜬다() throws Exception {
        uploadOk(null);
        long conversationId = start(lastDrawingId);
        answer(conversationId, "요즘은 가끔 그냥 사라지고 싶어요")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.question").isEmpty())
                .andExpect(jsonPath("$.data.supportNotice.phone").value("109"))
                .andExpect(jsonPath("$.data.supportNotice.actionLabel").value("조금 더 전문적인 상담 받기"));
    }

    @Test
    void AI가_실패해도_기본_질문과_기본_카드로_흐름이_이어진다() throws Exception {
        aiFails = true;
        uploadOk("빨간 우산");
        long conversationId = number(mvc.perform(post("/api/conversations").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"drawingId\":" + lastDrawingId + "}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.fallback").value(true))
                .andExpect(jsonPath("$.data.question.content").isNotEmpty()), "$.data.conversationId");
        answer(conversationId, "우산을 쓴 사람을 그렸어요")
                .andExpect(jsonPath("$.data.fallback").value(true))
                .andExpect(jsonPath("$.data.question.order").value(2));
        end(conversationId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.fallback").value(true))
                .andExpect(jsonPath("$.data.card.whatISaid").value("우산을 쓴 사람을 그렸어요"));
    }

    @Test
    void 양털이_부족하거나_연장_한도를_넘으면_409() throws Exception {
        uploadOk(null);
        long conversationId = start(lastDrawingId);
        for (int i = 1; i <= 5; i++) {
            answer(conversationId, "답 " + i);
        }
        send(conversationId, "{\"useWool\":true}")
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("NOT_ENOUGH_WOOL"));

        woolBalance = 10;
        send(conversationId, "{\"useWool\":true}").andExpect(jsonPath("$.data.question.order").value(6));
        for (int order = 7; order <= 10; order++) {
            send(conversationId, "{\"type\":\"ANSWER\",\"content\":\"더 이야기\",\"useWool\":true}")
                    .andExpect(jsonPath("$.data.question.order").value(order))
                    .andExpect(jsonPath("$.data.woolUsed").value(1));
        }
        assertThat(woolBalance).isEqualTo(5);
        send(conversationId, "{\"type\":\"ANSWER\",\"content\":\"마지막\",\"useWool\":true}")
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("EXTEND_LIMIT_REACHED"));
        // 한도에 닿은 뒤 양털 없이 답하면 마치기만 남는다
        answer(conversationId, "마지막")
                .andExpect(jsonPath("$.data.question").isEmpty())
                .andExpect(jsonPath("$.data.canExtend").value(false));
    }

    @Test
    void 진행_중인_대화_조회() throws Exception {
        userId = 77; // 다른 테스트의 대화와 섞이지 않게
        mvc.perform(get("/api/conversations").param("status", "IN_PROGRESS"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.conversation").isEmpty());
        uploadOk(null);
        long conversationId = start(lastDrawingId);
        mvc.perform(get("/api/conversations").param("status", "IN_PROGRESS"))
                .andExpect(jsonPath("$.data.conversation.conversationId").value(conversationId))
                .andExpect(jsonPath("$.data.conversation.remainingFreeQuestions").value(4));
        mvc.perform(get("/api/conversations").param("status", "ENDED"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("INVALID_STATUS"));
        mvc.perform(get("/api/conversations")).andExpect(status().isBadRequest());
        end(conversationId);
        mvc.perform(get("/api/conversations").param("status", "IN_PROGRESS"))
                .andExpect(jsonPath("$.data.conversation").isEmpty());
    }

    // ---- helpers ----

    long lastDrawingId;

    static MockMultipartFile png() {
        return new MockMultipartFile("image", "drawing.png", "image/png", PNG);
    }

    static byte[] bigPng() {
        byte[] big = new byte[10 * 1024 * 1024 + 1];
        System.arraycopy(PNG, 0, big, 0, PNG.length);
        return big;
    }

    ResultActions upload(MockMultipartFile file, String inputType, String topicId, String note) throws Exception {
        var request = multipart("/api/drawings");
        if (file != null) {
            request.file(file);
        }
        if (inputType != null) {
            request.param("inputType", inputType);
        }
        if (topicId != null) {
            request.param("topicId", topicId);
        }
        if (note != null) {
            request.param("note", note);
        }
        return mvc.perform(request);
    }

    ResultActions uploadOk(String note) throws Exception {
        ResultActions result = upload(png(), "PHOTO", "3", note)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.inputType").value("PHOTO"))
                .andExpect(jsonPath("$.data.topicId").value(3))
                .andExpect(jsonPath("$.data.createdAt").value(org.hamcrest.Matchers.endsWith("+09:00")));
        lastDrawingId = number(result, "$.data.drawingId");
        return result;
    }

    long start(long drawingId) throws Exception {
        return number(mvc.perform(post("/api/conversations").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"drawingId\":" + drawingId + "}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.question.order").value(1))
                .andExpect(jsonPath("$.data.freeQuestions").value(5))
                .andExpect(jsonPath("$.data.fallback").value(false)), "$.data.conversationId");
    }

    ResultActions send(long conversationId, String json) throws Exception {
        return mvc.perform(post("/api/conversations/" + conversationId + "/messages")
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    ResultActions answer(long conversationId, String content) throws Exception {
        return send(conversationId, "{\"type\":\"ANSWER\",\"content\":\"" + content + "\"}");
    }

    ResultActions end(long conversationId) throws Exception {
        return mvc.perform(put("/api/conversations/" + conversationId).contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"ENDED\"}"));
    }

    static long number(ResultActions result, String path) throws Exception {
        String body = result.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return ((Number) JsonPath.read(body, path)).longValue();
    }
}
