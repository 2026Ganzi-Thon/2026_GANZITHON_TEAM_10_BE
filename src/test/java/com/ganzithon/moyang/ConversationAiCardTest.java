package com.ganzithon.moyang;

import static org.assertj.core.api.Assertions.assertThat;

import com.ganzithon.moyang.ai.client.AiClient;
import com.ganzithon.moyang.ai.service.ConversationAi;
import com.ganzithon.moyang.ai.service.ConversationAi.Card;
import com.ganzithon.moyang.ai.service.ConversationAi.Context;
import com.ganzithon.moyang.ai.service.ConversationAi.Line;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/**
 * 회고 카드의 "없음" 처리. AI가 todaysStory에 "없음"을 주면 기본 카드로 넘어가고, thoughts의 "없음"은 비우고,
 * '그림 자세히 살펴보기' 네 칸의 "없음"은 기존대로 null이 된다. 스프링 없이 가짜 AI로 확인한다.
 */
class ConversationAiCardTest {

    static final String FALLBACK_STORY = "그림을 그리고, 그 그림에 대해 이야기해 본 하루였어요.";
    static final Context CTX = new Context(null, "자유 주제", null, "우산을 쓴 사람");
    static final List<Line> SAID = List.of(
            new Line(true, "무엇을 그렸나요?", true),
            new Line(false, "우산을 쓴 사람을 그렸어요", true));

    /** 주어진 칸 값으로 카드 JSON을 돌려주는 가짜 AI. 받은 지시문은 system에 담아 둔다. */
    static ConversationAi ai(AtomicReference<String> system, String whatISaid, String thoughts, String todaysStory,
                             String mainElements, String spatial, String line, String emotion) {
        AiClient client = (sys, user, schemaName, schema) -> {
            system.set(sys);
            return json(whatISaid, thoughts, todaysStory, mainElements, spatial, line, emotion);
        };
        return new ConversationAi(client);
    }

    static String json(String... v) {
        String[] keys = {"whatISaid", "thoughts", "todaysStory", "mainElements", "spatialComposition",
                "lineExpression", "emotionMeaning"};
        StringBuilder sb = new StringBuilder("{");
        for (int i = 0; i < keys.length; i++) {
            sb.append(i == 0 ? "" : ",").append('"').append(keys[i]).append("\":\"")
                    .append(v[i].replace("\\", "\\\\").replace("\"", "\\\"")).append('"');
        }
        return sb.append('}').toString();
    }

    Card card(String whatISaid, String thoughts, String todaysStory) {
        return ai(new AtomicReference<>(), whatISaid, thoughts, todaysStory, "없음", "없음", "없음", "없음")
                .card(CTX, SAID);
    }

    // ── todaysStory가 없음·빈 값이면 기본 카드 ──

    @Test
    void todaysStoryNoneFallsBackToTheDefaultCard() {
        Card c = card("우산을 쓴 사람을 그렸어요", "비 오는 날이 떠올랐어요", "없음");
        assertThat(c.fallback()).isTrue();
        assertThat(c.todaysStory()).isEqualTo(FALLBACK_STORY);
        assertThat(c.whatISaid()).isEqualTo("우산을 쓴 사람을 그렸어요"); // 사용자가 한 말로 만든 기본 카드
        assertThat(c.analysis()).isNull();
    }

    @Test
    void noneWrittenWithDotsQuotesOrSpacesAlsoCounts() {
        for (String none : new String[] {"없음.", " 없음 ", "\"없음\"", "'없음'", "없 음"}) {
            Card c = card("우산을 쓴 사람을 그렸어요", "생각", none);
            assertThat(c.fallback()).as("todaysStory=[%s]", none).isTrue();
            assertThat(c.todaysStory()).as("todaysStory=[%s]", none).isEqualTo(FALLBACK_STORY);
        }
    }

    @Test
    void emptyTodaysStoryStillFallsBack() {
        Card c = card("우산을 쓴 사람을 그렸어요", "생각", "   ");
        assertThat(c.fallback()).isTrue();
        assertThat(c.todaysStory()).isEqualTo(FALLBACK_STORY);
    }

    @Test
    void whatISaidNoneFallsBackToTheDefaultCardBuiltFromWhatTheUserSaid() {
        for (String none : new String[] {"없음", " 없음. ", "\"없음\"", ""}) {
            Card c = card(none, "생각", "오늘은 마음이 차분했어요");
            assertThat(c.fallback()).as("whatISaid=[%s]", none).isTrue();
            assertThat(c.whatISaid()).as("whatISaid=[%s]", none).isEqualTo("우산을 쓴 사람을 그렸어요");
            assertThat(c.todaysStory()).as("whatISaid=[%s]", none).isEqualTo(FALLBACK_STORY);
        }
    }

    @Test
    void aRealTodaysStoryIsKept() {
        Card c = card("우산을 쓴 사람을 그렸어요", "비 오는 날이 떠올랐어요", "오늘은 마음이 차분했어요");
        assertThat(c.fallback()).isFalse();
        assertThat(c.todaysStory()).isEqualTo("오늘은 마음이 차분했어요");
        assertThat(c.thoughts()).isEqualTo("비 오는 날이 떠올랐어요");
    }

    // ── thoughts가 없음이면 null ──

    @Test
    void thoughtsNoneBecomesNullWithoutFallingBack() {
        Card c = card("우산을 쓴 사람을 그렸어요", "없음", "오늘은 마음이 차분했어요");
        assertThat(c.fallback()).isFalse();
        assertThat(c.thoughts()).isNull();
        assertThat(c.todaysStory()).isEqualTo("오늘은 마음이 차분했어요");
        assertThat(card("우산을 쓴 사람을 그렸어요", " 없음. ", "오늘은 마음이 차분했어요").thoughts()).isNull();
        assertThat(card("우산을 쓴 사람을 그렸어요", "", "오늘은 마음이 차분했어요").thoughts()).isNull();
    }

    // ── '그림 자세히 살펴보기' 네 칸의 없음 처리는 그대로 ──

    @Test
    void analysisFieldsNoneStillBecomeNull() {
        Card c = ai(new AtomicReference<>(), "우산을 쓴 사람을 그렸어요", "생각", "오늘은 마음이 차분했어요",
                "우산을 그렸어요", "없음", " 없음. ", "\"없음\"").card(CTX, SAID);
        assertThat(c.fallback()).isFalse();
        assertThat(c.analysis().mainElements()).isEqualTo("우산을 그렸어요"); // 말한 칸은 그대로
        assertThat(c.analysis().spatialComposition()).isNull();
        assertThat(c.analysis().lineExpression()).isNull();
        assertThat(c.analysis().emotionMeaning()).isNull();
    }

    // ── 지시문 ──

    @Test
    void theCardPromptForbidsNoneForWhatISaidAndTodaysStoryButKeepsTheAnalysisRule() {
        AtomicReference<String> system = new AtomicReference<>();
        ai(system, "우산을 쓴 사람을 그렸어요", "생각", "오늘은 마음이 차분했어요", "없음", "없음", "없음", "없음")
                .card(CTX, SAID);
        assertThat(system.get())
                .contains("whatISaid와 todaysStory는 어떤 경우에도 \"없음\"이라고 쓰지 마")
                .contains("그 칸에 해당하는 말을 내가 하지 않았으면 정확히 \"없음\"이라고만 써"); // 기존 네 칸 규칙
    }
}
