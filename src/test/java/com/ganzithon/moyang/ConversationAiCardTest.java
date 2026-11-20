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

    // ── todaysStory가 없음·빈 값이면 그 칸에만 기본 문장 (나머지는 AI가 만든 그대로) ──

    @Test
    void todaysStoryNoneOnlyThatFieldGetsTheDefaultSentenceAndTheRestIsKept() {
        Card c = ai(new AtomicReference<>(), "AI가 정리한 한 일", "비 오는 날이 떠올랐어요", "없음",
                "우산을 그렸어요", "사람이 가운데 있어요", "굵은 선으로 그렸어요", "차분한 마음이에요").card(CTX, SAID);
        assertThat(c.fallback()).isFalse(); // AI 카드는 그대로
        assertThat(c.todaysStory()).isEqualTo(FALLBACK_STORY); // 이 칸만 기본 문장
        assertThat(c.whatISaid()).isEqualTo("AI가 정리한 한 일");
        assertThat(c.thoughts()).isEqualTo("비 오는 날이 떠올랐어요");
        assertThat(c.analysis()).isNotNull();
        assertThat(c.analysis().mainElements()).isEqualTo("우산을 그렸어요");
        assertThat(c.analysis().spatialComposition()).isEqualTo("사람이 가운데 있어요");
        assertThat(c.analysis().lineExpression()).isEqualTo("굵은 선으로 그렸어요");
        assertThat(c.analysis().emotionMeaning()).isEqualTo("차분한 마음이에요");
    }

    @Test
    void noneWrittenWithDotsQuotesOrSpacesAlsoCounts() {
        for (String none : new String[] {"없음.", " 없음 ", "\"없음\"", "'없음'", "없 음"}) {
            Card c = card("AI가 정리한 한 일", "생각", none);
            assertThat(c.fallback()).as("todaysStory=[%s]", none).isFalse();
            assertThat(c.todaysStory()).as("todaysStory=[%s]", none).isEqualTo(FALLBACK_STORY);
            assertThat(c.whatISaid()).as("todaysStory=[%s]", none).isEqualTo("AI가 정리한 한 일");
        }
    }

    @Test
    void emptyTodaysStoryAlsoGetsTheDefaultSentenceWithoutFallingBack() {
        Card c = card("AI가 정리한 한 일", "생각", "   ");
        assertThat(c.fallback()).isFalse();
        assertThat(c.todaysStory()).isEqualTo(FALLBACK_STORY);
        assertThat(c.whatISaid()).isEqualTo("AI가 정리한 한 일");
        assertThat(c.thoughts()).isEqualTo("생각");
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

    // ── 말하지 않은 해석 막기 ──

    @Test
    void theCardPromptOnlyAllowsFeelingsTheUserActuallySaid() {
        AtomicReference<String> system = new AtomicReference<>();
        ai(system, "우산을 쓴 사람을 그렸어요", "없음", "비가 와서 우산 쓴 사람을 그렸어요", "없음", "없음", "없음", "없음")
                .card(CTX, SAID);
        assertThat(system.get())
                .doesNotContain("todaysStory: 오늘의 마음 이야기") // 마음을 지어내게 하던 칸 정의
                .contains("todaysStory: 내가 한 말 전체를 아우르는 한 줄 정리")
                .contains("내가 직접 말했을 때만 써")
                .contains("내가 마음을 말하지 않았으면 todaysStory에는 무엇을 그렸는지만 한 줄로 정리해");
    }

    @Test
    void todaysStoryCopyingTheFieldDescriptionGetsTheDefaultSentence() {
        for (String leaked : new String[] {"오늘의 마음 이야기에요", "매일 걷는 길이 오늘의 마음 이야기예요."}) {
            Card c = card("우산을 쓴 사람을 그렸어요", "없음", leaked);
            assertThat(c.fallback()).as("todaysStory=[%s]", leaked).isFalse();
            assertThat(c.todaysStory()).as("todaysStory=[%s]", leaked).isEqualTo(FALLBACK_STORY);
            assertThat(c.whatISaid()).isEqualTo("우산을 쓴 사람을 그렸어요"); // 나머지 AI 결과는 그대로
        }
    }
}
