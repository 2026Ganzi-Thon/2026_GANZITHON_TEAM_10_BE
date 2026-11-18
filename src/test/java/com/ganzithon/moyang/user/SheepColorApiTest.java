package com.ganzithon.moyang.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ganzithon.moyang.auth.JwtTokenProvider;
import com.ganzithon.moyang.common.exception.BusinessException;
import com.ganzithon.moyang.common.exception.ErrorCode;
import com.ganzithon.moyang.user.domain.User;
import com.ganzithon.moyang.user.dto.UserDtos.SheepColorRequest;
import com.ganzithon.moyang.user.repository.UserRepository;
import com.ganzithon.moyang.user.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:sheep-color-api;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "moyang.upload-dir=build/test-uploads"
})
class SheepColorApiTest {

    static final String ME = "/api/users/me";
    static final String URL = ME + "/sheep-color";

    @Autowired WebApplicationContext context;
    @Autowired JwtTokenProvider tokens;
    @Autowired UserRepository users;
    @Autowired UserService userService;

    MockMvc mvc() {
        return MockMvcBuilders.webAppContextSetup(context).build();
    }

    User newUser(String loginId) {
        return users.save(new User(loginId, "x", "닉네임"));
    }

    String bearer(User u) {
        return "Bearer " + tokens.issue(u.getId());
    }

    ResultActions patchColor(User u, String json) throws Exception {
        return mvc().perform(patch(URL).header("Authorization", bearer(u))
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    ResultActions getMe(User u) throws Exception {
        return mvc().perform(get(ME).header("Authorization", bearer(u)));
    }

    void expectInvalid(User u, String json) throws Exception {
        patchColor(u, json).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
    }

    @Test
    void savesAndShowsInMyInfo() throws Exception {
        User u = newUser("color-save");
        getMe(u).andExpect(jsonPath("$.data.sheepColor").doesNotExist());

        patchColor(u, "{\"sheepColor\": \"PINK\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.userId").value(u.getId()))
                .andExpect(jsonPath("$.data.sheepColor").value("PINK"));

        getMe(u).andExpect(jsonPath("$.data.sheepColor").value("PINK"));
        assertThat(users.findById(u.getId()).orElseThrow().getSheepColor()).isEqualTo("PINK");
    }

    @Test
    void canChangeAgain() throws Exception {
        User u = newUser("color-change");
        patchColor(u, "{\"sheepColor\": \"PINK\"}").andExpect(status().isOk());
        patchColor(u, "{\"sheepColor\": \"SKY_BLUE_2\"}").andExpect(status().isOk());

        getMe(u).andExpect(jsonPath("$.data.sheepColor").value("SKY_BLUE_2"));
    }

    @Test
    void serverChecksFormatOnly() throws Exception {
        // 코드값은 프론트가 정하므로, 형식만 맞으면 어떤 값이든 받는다
        User u = newUser("color-format-only");
        patchColor(u, "{\"sheepColor\": \"ANYTHING_I_LIKE_123\"}").andExpect(status().isOk());
        patchColor(u, "{\"sheepColor\": \"A\"}").andExpect(status().isOk());
    }

    @Test
    void lengthBoundary() throws Exception {
        User u = newUser("color-length");
        patchColor(u, "{\"sheepColor\": \"" + "A".repeat(20) + "\"}").andExpect(status().isOk());
        expectInvalid(u, "{\"sheepColor\": \"" + "A".repeat(21) + "\"}");
        getMe(u).andExpect(jsonPath("$.data.sheepColor").value("A".repeat(20)));
    }

    @Test
    void badFormatsAreInvalidRequest() throws Exception {
        User u = newUser("color-bad");
        patchColor(u, "{\"sheepColor\": \"PINK\"}").andExpect(status().isOk());

        expectInvalid(u, "{\"sheepColor\": \"pink\"}");
        expectInvalid(u, "{\"sheepColor\": \"HOT-PINK\"}");
        expectInvalid(u, "{\"sheepColor\": \"분홍\"}");
        expectInvalid(u, "{\"sheepColor\": \"PINK \"}");
        expectInvalid(u, "{\"sheepColor\": \"\"}");
        expectInvalid(u, "{\"sheepColor\": \"  \"}");
        expectInvalid(u, "{\"sheepColor\": null}");
        expectInvalid(u, "{}");
        expectInvalid(u, "not json");

        // 실패한 요청은 기존 색을 바꾸지 않는다
        getMe(u).andExpect(jsonPath("$.data.sheepColor").value("PINK"));
    }

    @Test
    void worksBeforeOnboardingAndSurvivesOnboardingSave() throws Exception {
        User u = newUser("color-onboarding");
        patchColor(u, "{\"sheepColor\": \"MINT\"}").andExpect(status().isOk());
        getMe(u).andExpect(jsonPath("$.data.onboardingCompleted").value(false));

        mvc().perform(put(ME + "/onboarding").header("Authorization", bearer(u))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"themeCode\": \"LOVE\"}"))
                .andExpect(status().isOk());

        getMe(u).andExpect(jsonPath("$.data.onboardingCompleted").value(true))
                .andExpect(jsonPath("$.data.sheepColor").value("MINT"));
    }

    @Test
    void doesNotAffectOtherUsers() throws Exception {
        User a = newUser("color-a");
        User b = newUser("color-b");
        patchColor(a, "{\"sheepColor\": \"PINK\"}").andExpect(status().isOk());

        getMe(a).andExpect(jsonPath("$.data.sheepColor").value("PINK"));
        getMe(b).andExpect(jsonPath("$.data.sheepColor").doesNotExist());
    }

    @Test
    void requiresLogin() throws Exception {
        mvc().perform(patch(URL).contentType(MediaType.APPLICATION_JSON).content("{\"sheepColor\": \"PINK\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
        mvc().perform(patch(URL).header("Authorization", "Bearer not-a-token")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"sheepColor\": \"PINK\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void invalidRequestIsCheckedAfterLogin() throws Exception {
        // 로그인이 없으면 값이 틀려도 401이 먼저
        mvc().perform(patch(URL).contentType(MediaType.APPLICATION_JSON).content("{\"sheepColor\": \"bad\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void serviceThrowsUserNotFoundForUnknownUser() {
        assertThatThrownBy(() -> userService.changeSheepColor(987_654_321L, new SheepColorRequest("PINK")))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.USER_NOT_FOUND));
    }
}
