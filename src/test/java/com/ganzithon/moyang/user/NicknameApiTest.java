package com.ganzithon.moyang.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ganzithon.moyang.auth.JwtTokenProvider;
import com.ganzithon.moyang.common.exception.BusinessException;
import com.ganzithon.moyang.common.exception.ErrorCode;
import com.ganzithon.moyang.user.domain.User;
import com.ganzithon.moyang.user.dto.UserDtos.NicknameRequest;
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
        "spring.datasource.url=jdbc:h2:mem:nickname-api;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "moyang.upload-dir=build/test-uploads"
})
class NicknameApiTest {

    static final String ME = "/api/users/me";
    static final String URL = ME + "/nickname";

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

    /** 닉네임 값을 JSON 문자열로 감싸 보낸다. 줄바꿈 같은 제어 문자는 호출하는 쪽에서 \\n 처럼 이스케이프해서 넘긴다. */
    ResultActions patchNickname(User u, String jsonValue) throws Exception {
        return patchJson(u, "{\"nickname\": \"" + jsonValue + "\"}");
    }

    ResultActions patchJson(User u, String json) throws Exception {
        return mvc().perform(patch(URL).header("Authorization", bearer(u))
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    ResultActions getMe(User u) throws Exception {
        return mvc().perform(get(ME).header("Authorization", bearer(u)));
    }

    void expectInvalid(ResultActions r) throws Exception {
        r.andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("INVALID_NICKNAME"));
    }

    @Test
    void changesAndShowsInMyInfo() throws Exception {
        User u = newUser("nick-change", "졸린 양 #1");
        patchNickname(u, "딸기를 좋아하는 양")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.userId").value(u.getId()))
                .andExpect(jsonPath("$.data.nickname").value("딸기를 좋아하는 양"));

        getMe(u).andExpect(jsonPath("$.data.nickname").value("딸기를 좋아하는 양"));
        assertThat(users.findById(u.getId()).orElseThrow().getNickname()).isEqualTo("딸기를 좋아하는 양");
    }

    @Test
    void trimsSurroundingWhitespaceBeforeChecking() throws Exception {
        User u = newUser("nick-trim", "졸린 양 #2");
        patchNickname(u, "  가나다  ")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nickname").value("가나다"));
        patchNickname(u, "　가나　").andExpect(jsonPath("$.data.nickname").value("가나"));
        assertThat(users.findById(u.getId()).orElseThrow().getNickname()).isEqualTo("가나");

        // 공백을 지우면 1자가 되므로 거부
        expectInvalid(patchNickname(u, "  가  "));
        // 공백 때문에 길어 보여도 지운 뒤 15자면 통과
        patchNickname(u, "  " + "가".repeat(15) + "  ").andExpect(status().isOk());
    }

    @Test
    void lengthBoundary() throws Exception {
        User u = newUser("nick-length", "졸린 양 #3");
        patchNickname(u, "가나").andExpect(status().isOk());
        patchNickname(u, "가".repeat(15)).andExpect(status().isOk());
        expectInvalid(patchNickname(u, "가"));
        expectInvalid(patchNickname(u, "가".repeat(16)));
        getMe(u).andExpect(jsonPath("$.data.nickname").value("가".repeat(15)));
    }

    @Test
    void emptyBlankNullAndMissingAreInvalidNickname() throws Exception {
        User u = newUser("nick-empty", "졸린 양 #4");
        expectInvalid(patchNickname(u, ""));
        expectInvalid(patchNickname(u, "     "));
        expectInvalid(patchJson(u, "{\"nickname\": null}"));
        expectInvalid(patchJson(u, "{}"));
        getMe(u).andExpect(jsonPath("$.data.nickname").value("졸린 양 #4"));
    }

    @Test
    void malformedJsonIsInvalidRequest() throws Exception {
        User u = newUser("nick-json", "졸린 양 #5");
        patchJson(u, "not json").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
    }

    @Test
    void innerSpacesSpecialCharactersAndEmojiAreAllowed() throws Exception {
        User u = newUser("nick-special", "졸린 양 #6");
        for (String nickname : new String[] {"졸린 양 #3", "a  b", "★별★", "양!@#$%^&*()", "Sheep_01", "🐑🐑"}) {
            patchNickname(u, nickname)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.nickname").value(nickname));
        }
    }

    @Test
    void emojiCountsAsOneCharacter() throws Exception {
        User u = newUser("nick-emoji", "졸린 양 #7");
        patchNickname(u, "🐑".repeat(15)).andExpect(status().isOk());
        expectInvalid(patchNickname(u, "🐑".repeat(16)));
        expectInvalid(patchNickname(u, "🐑"));
    }

    @Test
    void defaultNicknameCanBeSavedAgain() throws Exception {
        User u = newUser("nick-default", "졸린 양 #3");
        patchNickname(u, "졸린 양 #3")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nickname").value("졸린 양 #3"));
    }

    @Test
    void duplicateNicknamesAreAllowed() throws Exception {
        User a = newUser("nick-dup-a", "졸린 양 #8");
        User b = newUser("nick-dup-b", "졸린 양 #9");
        patchNickname(a, "같은 이름").andExpect(status().isOk());
        patchNickname(b, "같은 이름").andExpect(status().isOk());

        getMe(a).andExpect(jsonPath("$.data.nickname").value("같은 이름"));
        getMe(b).andExpect(jsonPath("$.data.nickname").value("같은 이름"));
    }

    @Test
    void controlCharactersInsideAreRejected() throws Exception {
        User u = newUser("nick-control", "졸린 양 #10");
        expectInvalid(patchNickname(u, "가\\n나"));
        expectInvalid(patchNickname(u, "가\\t나"));
        expectInvalid(patchNickname(u, "가\\u0000나"));
        expectInvalid(patchNickname(u, "가\\u2028나"));
        // 앞뒤의 줄바꿈은 공백처럼 지워진다
        patchNickname(u, "가나\\n").andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nickname").value("가나"));
    }

    @Test
    void failedChangeKeepsPreviousNickname() throws Exception {
        User u = newUser("nick-keep", "졸린 양 #11");
        patchNickname(u, "바뀐 이름").andExpect(status().isOk());
        expectInvalid(patchNickname(u, "가"));
        assertThat(users.findById(u.getId()).orElseThrow().getNickname()).isEqualTo("바뀐 이름");
    }

    @Test
    void doesNotAffectOtherUsers() throws Exception {
        User a = newUser("nick-other-a", "양 A");
        User b = newUser("nick-other-b", "양 B");
        patchNickname(a, "새 이름").andExpect(status().isOk());

        getMe(b).andExpect(jsonPath("$.data.nickname").value("양 B"));
    }

    @Test
    void requiresLogin() throws Exception {
        mvc().perform(patch(URL).contentType(MediaType.APPLICATION_JSON).content("{\"nickname\": \"가나다\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
        mvc().perform(patch(URL).header("Authorization", "Bearer not-a-token")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"nickname\": \"가나다\"}"))
                .andExpect(status().isUnauthorized());
        // 로그인이 없으면 값이 틀려도 401이 먼저
        mvc().perform(patch(URL).contentType(MediaType.APPLICATION_JSON).content("{\"nickname\": \"가\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void serviceThrowsUserNotFoundForUnknownUser() {
        assertThatThrownBy(() -> userService.changeNickname(987_654_321L, new NicknameRequest("가나다")))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.USER_NOT_FOUND));
    }

    @Test
    void errorCodeUsesGeneralMessage() {
        assertThat(ErrorCode.INVALID_NICKNAME.getStatus().value()).isEqualTo(400);
        assertThat(ErrorCode.INVALID_NICKNAME.getMessage()).isEqualTo("닉네임은 2자 이상 15자 이하로 써 주세요.");
    }
}
