package com.ganzithon.moyang.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ganzithon.moyang.auth.JwtTokenProvider;
import com.ganzithon.moyang.user.domain.User;
import com.ganzithon.moyang.user.domain.UserOnboarding;
import com.ganzithon.moyang.user.repository.UserOnboardingRepository;
import com.ganzithon.moyang.user.repository.UserRepository;
import java.time.Year;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:onboarding-api;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "moyang.upload-dir=build/test-uploads"
})
class OnboardingApiTest {

    static final String URL = "/api/users/me/onboarding";

    @Autowired WebApplicationContext context;
    @Autowired JwtTokenProvider tokens;
    @Autowired UserRepository users;
    @Autowired UserOnboardingRepository onboardings;

    MockMvc mvc() {
        return MockMvcBuilders.webAppContextSetup(context).build();
    }

    User newUser(String loginId) {
        return users.save(new User(loginId, "x", "닉네임"));
    }

    String bearer(User u) {
        return "Bearer " + tokens.issue(u.getId());
    }

    ResultActions putJson(User u, String json) throws Exception {
        return mvc().perform(put(URL).header("Authorization", bearer(u))
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    ResultActions expectError(ResultActions r, int status, String code) throws Exception {
        return r.andExpect(status().is(status)).andExpect(jsonPath("$.error.code").value(code));
    }

    static final String FULL = """
            {
              "birthYear": 2003, "gender": null, "artExperience": "NONE",
              "themeCode": "STUDY_CAREER", "themeText": null,
              "lifeStatus": "STUDENT", "lifeStatusText": null,
              "emotions": ["ANXIOUS", "TIRED"], "emotionText": null,
              "extraStory": "요즘 진로 때문에 잠이 잘 안 와요"
            }""";

    @Test
    void savesAllAnswers() throws Exception {
        User u = newUser("ob-full");
        putJson(u, FULL)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.onboardingCompleted").value(true))
                .andExpect(jsonPath("$.data.onboarding.birthYear").value(2003))
                .andExpect(jsonPath("$.data.onboarding.gender").doesNotExist())
                .andExpect(jsonPath("$.data.onboarding.artExperience").value("NONE"))
                .andExpect(jsonPath("$.data.onboarding.themeCode").value("STUDY_CAREER"))
                .andExpect(jsonPath("$.data.onboarding.lifeStatus").value("STUDENT"))
                .andExpect(jsonPath("$.data.onboarding.emotions[0]").value("ANXIOUS"))
                .andExpect(jsonPath("$.data.onboarding.emotions[1]").value("TIRED"))
                .andExpect(jsonPath("$.data.onboarding.extraStory").value("요즘 진로 때문에 잠이 잘 안 와요"));

        User saved = users.findById(u.getId()).orElseThrow();
        assertThat(saved.isOnboardingCompleted()).isTrue();
        assertThat(saved.getThemeCode()).isEqualTo("STUDY_CAREER");
        UserOnboarding row = onboardings.findById(u.getId()).orElseThrow();
        assertThat(row.getEmotions()).containsExactly("ANXIOUS", "TIRED");
    }

    @Test
    void onlyThemeCodeIsRequired() throws Exception {
        User u = newUser("ob-min");
        putJson(u, "{\"themeCode\": \"LOVE\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.onboardingCompleted").value(true))
                .andExpect(jsonPath("$.data.onboarding.themeCode").value("LOVE"))
                .andExpect(jsonPath("$.data.onboarding.birthYear").doesNotExist())
                .andExpect(jsonPath("$.data.onboarding.emotions").isEmpty());
    }

    @Test
    void secondSaveOverwritesEverything() throws Exception {
        User u = newUser("ob-overwrite");
        putJson(u, FULL).andExpect(status().isOk());

        putJson(u, "{\"themeCode\": \"MONEY\", \"emotions\": [\"CALM\"]}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.onboarding.themeCode").value("MONEY"))
                .andExpect(jsonPath("$.data.onboarding.birthYear").doesNotExist())
                .andExpect(jsonPath("$.data.onboarding.lifeStatus").doesNotExist())
                .andExpect(jsonPath("$.data.onboarding.extraStory").doesNotExist())
                .andExpect(jsonPath("$.data.onboarding.emotions.length()").value(1));

        assertThat(onboardings.findAll().stream().filter(o -> o.getUserId().equals(u.getId()))).hasSize(1);
        assertThat(users.findById(u.getId()).orElseThrow().getThemeCode()).isEqualTo("MONEY");
    }

    @Test
    void requiresLogin() throws Exception {
        mvc().perform(put(URL).contentType(MediaType.APPLICATION_JSON).content(FULL))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
        mvc().perform(put(URL).header("Authorization", "Bearer not-a-token")
                        .contentType(MediaType.APPLICATION_JSON).content(FULL))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void missingThemeCodeIsInvalidRequest() throws Exception {
        User u = newUser("ob-no-theme");
        expectError(putJson(u, "{\"emotions\": [\"CALM\"]}"), 400, "INVALID_REQUEST");
        expectError(putJson(u, "{\"themeCode\": \"  \"}"), 400, "INVALID_REQUEST");
        expectError(putJson(u, "{}"), 400, "INVALID_REQUEST");
        expectError(putJson(u, "not json"), 400, "INVALID_REQUEST");
        assertThat(users.findById(u.getId()).orElseThrow().isOnboardingCompleted()).isFalse();
    }

    @Test
    void unknownOptionCodesAreInvalidOptionCode() throws Exception {
        User u = newUser("ob-bad-code");
        expectError(putJson(u, "{\"themeCode\": \"NOPE\"}"), 400, "INVALID_OPTION_CODE");
        expectError(putJson(u, "{\"themeCode\": \"LOVE\", \"emotions\": [\"HAPPY\"]}"), 400, "INVALID_OPTION_CODE");
        expectError(putJson(u, "{\"themeCode\": \"LOVE\", \"lifeStatus\": \"KING\"}"), 400, "INVALID_OPTION_CODE");
        expectError(putJson(u, "{\"themeCode\": \"LOVE\", \"artExperience\": \"GOD\"}"), 400, "INVALID_OPTION_CODE");
        // 소문자는 다른 코드
        expectError(putJson(u, "{\"themeCode\": \"love\"}"), 400, "INVALID_OPTION_CODE");
    }

    @Test
    void invalidRequestComesBeforeInvalidOptionCode() throws Exception {
        User u = newUser("ob-order");
        expectError(putJson(u, "{\"themeCode\": \"NOPE\", \"emotions\": [\"A\", \"B\", \"C\"]}"), 400, "INVALID_REQUEST");
    }

    @Test
    void threeEmotionsOrDuplicateEmotionsAreRejected() throws Exception {
        User u = newUser("ob-emotions");
        expectError(putJson(u, "{\"themeCode\": \"LOVE\", \"emotions\": [\"ANXIOUS\", \"TIRED\", \"CALM\"]}"),
                400, "INVALID_REQUEST");
        expectError(putJson(u, "{\"themeCode\": \"LOVE\", \"emotions\": [\"ANXIOUS\", \"ANXIOUS\"]}"),
                400, "INVALID_REQUEST");
        expectError(putJson(u, "{\"themeCode\": \"LOVE\", \"emotions\": [\"ANXIOUS\", null]}"),
                400, "INVALID_REQUEST");
    }

    @Test
    void directThemeNeedsText() throws Exception {
        User u = newUser("ob-direct-theme");
        expectError(putJson(u, "{\"themeCode\": \"DIRECT\"}"), 400, "INVALID_REQUEST");
        expectError(putJson(u, "{\"themeCode\": \"DIRECT\", \"themeText\": \"   \"}"), 400, "INVALID_REQUEST");
        putJson(u, "{\"themeCode\": \"DIRECT\", \"themeText\": \"  반려견 걱정  \"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.onboarding.themeText").value("반려견 걱정"));
    }

    @Test
    void etcLifeStatusAndDirectEmotionNeedTheirTexts() throws Exception {
        User u = newUser("ob-direct-texts");
        expectError(putJson(u, "{\"themeCode\": \"LOVE\", \"lifeStatus\": \"ETC\"}"), 400, "INVALID_REQUEST");
        expectError(putJson(u, "{\"themeCode\": \"LOVE\", \"emotions\": [\"DIRECT\"]}"), 400, "INVALID_REQUEST");
        putJson(u, """
                {"themeCode": "LOVE", "lifeStatus": "ETC", "lifeStatusText": "군인",
                 "emotions": ["DIRECT", "CALM"], "emotionText": "묘하게 평온해요"}""")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.onboarding.lifeStatusText").value("군인"))
                .andExpect(jsonPath("$.data.onboarding.emotionText").value("묘하게 평온해요"));
    }

    @Test
    void textsAreIgnoredWhenTheirOptionIsNotChosen() throws Exception {
        User u = newUser("ob-ignore-texts");
        putJson(u, """
                {"themeCode": "LOVE", "themeText": "무시돼요",
                 "lifeStatus": "STUDENT", "lifeStatusText": "무시돼요",
                 "emotions": ["CALM"], "emotionText": "무시돼요"}""")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.onboarding.themeText").doesNotExist())
                .andExpect(jsonPath("$.data.onboarding.lifeStatusText").doesNotExist())
                .andExpect(jsonPath("$.data.onboarding.emotionText").doesNotExist());
    }

    @Test
    void textLengthLimits() throws Exception {
        User u = newUser("ob-length");
        String s100 = "가".repeat(100);
        String s101 = "가".repeat(101);
        putJson(u, "{\"themeCode\": \"DIRECT\", \"themeText\": \"" + s100 + "\", \"extraStory\": \""
                + "나".repeat(500) + "\"}").andExpect(status().isOk());
        expectError(putJson(u, "{\"themeCode\": \"DIRECT\", \"themeText\": \"" + s101 + "\"}"),
                400, "INVALID_REQUEST");
        expectError(putJson(u, "{\"themeCode\": \"LOVE\", \"lifeStatus\": \"ETC\", \"lifeStatusText\": \"" + s101 + "\"}"),
                400, "INVALID_REQUEST");
        expectError(putJson(u, "{\"themeCode\": \"LOVE\", \"emotions\": [\"DIRECT\"], \"emotionText\": \"" + s101 + "\"}"),
                400, "INVALID_REQUEST");
        expectError(putJson(u, "{\"themeCode\": \"LOVE\", \"extraStory\": \"" + "나".repeat(501) + "\"}"),
                400, "INVALID_REQUEST");
    }

    @Test
    void genderRule() throws Exception {
        User u = newUser("ob-gender");
        putJson(u, "{\"themeCode\": \"LOVE\", \"gender\": \"FEMALE\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.onboarding.gender").value("FEMALE"));
        putJson(u, "{\"themeCode\": \"LOVE\", \"gender\": \"NON_BINARY\"}").andExpect(status().isOk());
        expectError(putJson(u, "{\"themeCode\": \"LOVE\", \"gender\": \"female\"}"), 400, "INVALID_REQUEST");
        expectError(putJson(u, "{\"themeCode\": \"LOVE\", \"gender\": \"\"}"), 400, "INVALID_REQUEST");
        expectError(putJson(u, "{\"themeCode\": \"LOVE\", \"gender\": \"" + "A".repeat(21) + "\"}"),
                400, "INVALID_REQUEST");
        expectError(putJson(u, "{\"themeCode\": \"LOVE\", \"gender\": \"여성\"}"), 400, "INVALID_REQUEST");
    }

    @Test
    void birthYearRange() throws Exception {
        User u = newUser("ob-birth");
        int thisYear = Year.now(ZoneId.of("Asia/Seoul")).getValue();
        putJson(u, "{\"themeCode\": \"LOVE\", \"birthYear\": 1900}").andExpect(status().isOk());
        putJson(u, "{\"themeCode\": \"LOVE\", \"birthYear\": " + thisYear + "}").andExpect(status().isOk());
        expectError(putJson(u, "{\"themeCode\": \"LOVE\", \"birthYear\": 1899}"), 400, "INVALID_REQUEST");
        expectError(putJson(u, "{\"themeCode\": \"LOVE\", \"birthYear\": " + (thisYear + 1) + "}"),
                400, "INVALID_REQUEST");
        expectError(putJson(u, "{\"themeCode\": \"LOVE\", \"birthYear\": \"abc\"}"), 400, "INVALID_REQUEST");
    }

    @Test
    void failedSaveKeepsPreviousAnswers() throws Exception {
        User u = newUser("ob-keep");
        putJson(u, FULL).andExpect(status().isOk());
        expectError(putJson(u, "{\"themeCode\": \"NOPE\"}"), 400, "INVALID_OPTION_CODE");
        assertThat(onboardings.findById(u.getId()).orElseThrow().getThemeCode()).isEqualTo("STUDY_CAREER");
        assertThat(users.findById(u.getId()).orElseThrow().getThemeCode()).isEqualTo("STUDY_CAREER");
    }

    @Test
    void afterSavingRecommendedTopicsGoFrom409To200() throws Exception {
        User u = newUser("ob-topics");
        expectError(mvc().perform(get("/api/topics/recommended").header("Authorization", bearer(u))),
                409, "ONBOARDING_NOT_COMPLETED");

        putJson(u, "{\"themeCode\": \"LOVE\"}").andExpect(status().isOk());

        mvc().perform(get("/api/topics/recommended").header("Authorization", bearer(u)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.themeCode").value("LOVE"))
                .andExpect(jsonPath("$.data.topics[0].topicId").value(21));
    }
}
