package com.ganzithon.moyang;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/** 로그인 구현이 없고 local 프로필도 아니면 가짜 인증(X-User-Id)이 아니라 401로 막혀야 한다. */
@SpringBootTest
class PortFallbacksTest {

    @Autowired
    WebApplicationContext context;

    @Test
    void 인증_구현이_없으면_X_User_Id를_보내도_401() throws Exception {
        MockMvcBuilders.webAppContextSetup(context).build()
                .perform(get("/api/drawings/1").header("X-User-Id", "1"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }
}
