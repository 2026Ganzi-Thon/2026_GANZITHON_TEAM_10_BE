package com.ganzithon.moyang.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:login-api;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "moyang.upload-dir=build/test-uploads",
        "moyang.demo.password=login-test-pw-1"
})
class LoginApiTest {

    @Autowired WebApplicationContext context;

    MockMvc mvc() {
        return MockMvcBuilders.webAppContextSetup(context).build();
    }

    private static String body(String id, String pw) {
        return "{\"loginId\":\"" + id + "\",\"password\":\"" + pw + "\"}";
    }

    @Test
    void loginSucceedsWithDemoAccount() throws Exception {
        mvc().perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(body("demo1", "login-test-pw-1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.data.expiresIn").value(86400))
                .andExpect(jsonPath("$.data.user.nickname").value("졸린 양 #1"))
                .andExpect(jsonPath("$.data.user.onboardingCompleted").value(false));
    }

    @Test
    void wrongPasswordAndUnknownIdBothGive401WithSameCode() throws Exception {
        for (String[] c : new String[][] {{"demo1", "wrong-pw"}, {"nobody", "login-test-pw-1"}}) {
            mvc().perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(body(c[0], c[1])))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.error.code").value("INVALID_CREDENTIALS"));
        }
    }

    @Test
    void missingFieldsGive400() throws Exception {
        mvc().perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("{\"loginId\":\"demo1\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
    }
}
