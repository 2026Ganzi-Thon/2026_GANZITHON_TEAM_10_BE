package com.ganzithon.moyang.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ganzithon.moyang.auth.JwtTokenProvider;
import com.ganzithon.moyang.common.exception.BusinessException;
import com.ganzithon.moyang.common.exception.ErrorCode;
import com.ganzithon.moyang.user.domain.User;
import com.ganzithon.moyang.user.repository.UserRepository;
import com.ganzithon.moyang.user.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:user-me-api;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "moyang.upload-dir=build/test-uploads"
})
class UserMeApiTest {

    static final String ME = "/api/users/me";

    @Autowired WebApplicationContext context;
    @Autowired JwtTokenProvider tokens;
    @Autowired UserRepository users;
    @Autowired UserService userService;

    MockMvc mvc() {
        return MockMvcBuilders.webAppContextSetup(context).build();
    }

    User newUser(String loginId, String nickname) {
        return users.save(new User(loginId, "x", nickname));
    }

    String bearer(User u) {
        return "Bearer " + tokens.issue(u.getId());
    }

    ResultActions getMe(User u) throws Exception {
        return mvc().perform(get(ME).header("Authorization", bearer(u)));
    }

    ResultActions saveOnboarding(User u, String json) throws Exception {
        return mvc().perform(put(ME + "/onboarding").header("Authorization", bearer(u))
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    @Test
    void beforeOnboardingOnboardingAndSheepColorAreNull() throws Exception {
        User u = newUser("me-before", "졸린 양 #3");
        getMe(u).andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.userId").value(u.getId()))
                .andExpect(jsonPath("$.data.nickname").value("졸린 양 #3"))
                .andExpect(jsonPath("$.data.onboardingCompleted").value(false))
                .andExpect(jsonPath("$.data.sheepColor").doesNotExist())
                .andExpect(jsonPath("$.data.onboarding").doesNotExist());
    }

    @Test
    void afterOnboardingReturnsSavedAnswers() throws Exception {
        User u = newUser("me-after", "졸린 양 #4");
        saveOnboarding(u, """
                {"birthYear": 2003, "artExperience": "NONE", "themeCode": "STUDY_CAREER",
                 "lifeStatus": "STUDENT", "emotions": ["ANXIOUS", "TIRED"],
                 "extraStory": "요즘 진로 때문에 잠이 잘 안 와요"}""").andExpect(status().isOk());

        getMe(u).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.onboardingCompleted").value(true))
                .andExpect(jsonPath("$.data.onboarding.birthYear").value(2003))
                .andExpect(jsonPath("$.data.onboarding.artExperience").value("NONE"))
                .andExpect(jsonPath("$.data.onboarding.themeCode").value("STUDY_CAREER"))
                .andExpect(jsonPath("$.data.onboarding.lifeStatus").value("STUDENT"))
                .andExpect(jsonPath("$.data.onboarding.emotions[0]").value("ANXIOUS"))
                .andExpect(jsonPath("$.data.onboarding.emotions[1]").value("TIRED"))
                .andExpect(jsonPath("$.data.onboarding.extraStory").value("요즘 진로 때문에 잠이 잘 안 와요"));
    }

    @Test
    void skippedAnswersComeBackAsNull() throws Exception {
        User u = newUser("me-skipped", "닉네임");
        saveOnboarding(u, "{\"themeCode\": \"LOVE\"}").andExpect(status().isOk());

        getMe(u).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.onboardingCompleted").value(true))
                .andExpect(jsonPath("$.data.onboarding.themeCode").value("LOVE"))
                .andExpect(jsonPath("$.data.onboarding.birthYear").doesNotExist())
                .andExpect(jsonPath("$.data.onboarding.gender").doesNotExist())
                .andExpect(jsonPath("$.data.onboarding.lifeStatus").doesNotExist())
                .andExpect(jsonPath("$.data.onboarding.extraStory").doesNotExist())
                .andExpect(jsonPath("$.data.onboarding.emotions").isEmpty());
    }

    @Test
    void savingAgainShowsLatestAnswers() throws Exception {
        User u = newUser("me-resave", "닉네임");
        saveOnboarding(u, "{\"themeCode\": \"LOVE\", \"birthYear\": 2000}").andExpect(status().isOk());
        saveOnboarding(u, "{\"themeCode\": \"MONEY\"}").andExpect(status().isOk());

        getMe(u).andExpect(jsonPath("$.data.onboarding.themeCode").value("MONEY"))
                .andExpect(jsonPath("$.data.onboarding.birthYear").doesNotExist());
    }

    @Test
    void eachUserSeesOnlyTheirOwnInfo() throws Exception {
        User a = newUser("me-a", "양 A");
        User b = newUser("me-b", "양 B");
        saveOnboarding(a, "{\"themeCode\": \"LOVE\"}").andExpect(status().isOk());

        getMe(a).andExpect(jsonPath("$.data.nickname").value("양 A"))
                .andExpect(jsonPath("$.data.onboarding.themeCode").value("LOVE"));
        getMe(b).andExpect(jsonPath("$.data.nickname").value("양 B"))
                .andExpect(jsonPath("$.data.onboardingCompleted").value(false))
                .andExpect(jsonPath("$.data.onboarding").doesNotExist());
    }

    @Test
    void returnsSheepColorWhenChosen() throws Exception {
        // 색을 바꾸는 API는 다음 단위에서 만든다. 여기서는 저장된 값이 응답에 실리는지만 본다.
        User u = newUser("me-color", "닉네임");
        ReflectionTestUtils.setField(u, "sheepColor", "PINK");
        users.save(u);

        getMe(u).andExpect(status().isOk()).andExpect(jsonPath("$.data.sheepColor").value("PINK"));
    }

    @Test
    void requiresLogin() throws Exception {
        mvc().perform(get(ME))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
        mvc().perform(get(ME).header("Authorization", "Bearer not-a-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tokenOfDeletedUserIsUnauthorized() throws Exception {
        User u = newUser("me-gone", "닉네임");
        String auth = bearer(u);
        users.deleteById(u.getId());

        mvc().perform(get(ME).header("Authorization", auth)).andExpect(status().isUnauthorized());
    }

    @Test
    void serviceThrowsUserNotFoundForUnknownUser() {
        // HTTP에서는 로그인 확인이 먼저 401로 막으므로, 확인과 조회 사이에 사용자가 사라지는 경우를 서비스로 직접 확인한다.
        assertThatThrownBy(() -> userService.me(987_654_321L))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.USER_NOT_FOUND));
        assertThat(ErrorCode.USER_NOT_FOUND.getStatus().value()).isEqualTo(404);
    }
}
