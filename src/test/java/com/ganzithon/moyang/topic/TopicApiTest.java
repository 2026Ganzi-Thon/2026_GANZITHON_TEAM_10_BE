package com.ganzithon.moyang.topic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ganzithon.moyang.auth.JwtTokenProvider;
import com.ganzithon.moyang.common.port.Ports.TopicReader;
import com.ganzithon.moyang.topic.domain.Topic;
import com.ganzithon.moyang.topic.repository.TopicRepository;
import com.ganzithon.moyang.user.domain.User;
import com.ganzithon.moyang.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:topic-api;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "moyang.upload-dir=build/test-uploads"
})
class TopicApiTest {

    @Autowired WebApplicationContext context;
    @Autowired JwtTokenProvider tokens;
    @Autowired UserRepository users;
    @Autowired TopicRepository topics;
    @Autowired TopicReader reader;

    MockMvc mvc() {
        return MockMvcBuilders.webAppContextSetup(context).build();
    }

    String bearer(String loginId, String themeCode) {
        User u = new User(loginId, "x", "닉네임");
        u.changeThemeCode(themeCode);
        return "Bearer " + tokens.issue(users.save(u).getId());
    }

    @Test
    void seededTopicsExistWithFixedFreeTopicId() {
        assertThat(topics.findById(Topic.FREE_TOPIC_ID)).isPresent();
        assertThat(reader.find(Topic.FREE_TOPIC_ID)).isPresent();
        assertThat(reader.find(11).orElseThrow().title()).isEqualTo("내가 서 있는 자리");
        assertThat(reader.find(99999)).isEmpty();
    }

    @Test
    void everyThemeHasTwoOrThreeTopicsAndFreeTopicIsNotListed() throws Exception {
        String auth = bearer("topic-user-all", null);
        for (String code : new String[] {"STUDY_CAREER", "LOVE", "MONEY", "DAILY_FUTURE", "SELF_HEALTH",
                "RELATION_FAMILY", "UNSURE", "DIRECT"}) {
            mvc().perform(get("/api/topics/recommended").param("themeCode", code).header("Authorization", auth))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.themeCode").value(code))
                    .andExpect(jsonPath("$.data.topics.length()", org.hamcrest.Matchers.allOf(
                            org.hamcrest.Matchers.greaterThanOrEqualTo(2),
                            org.hamcrest.Matchers.lessThanOrEqualTo(3))))
                    .andExpect(jsonPath("$.data.topics[?(@.topicId==1)]").isEmpty());
        }
    }

    @Test
    void withoutThemeCodeUsesMyThemeFromOnboarding() throws Exception {
        mvc().perform(get("/api/topics/recommended").header("Authorization", bearer("topic-user-love", "LOVE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.themeCode").value("LOVE"))
                .andExpect(jsonPath("$.data.topics[0].topicId").value(21))
                .andExpect(jsonPath("$.data.topics[0].hints").isArray());
    }

    @Test
    void errorCases() throws Exception {
        mvc().perform(get("/api/topics/recommended")).andExpect(status().isUnauthorized());
        mvc().perform(get("/api/topics/recommended").header("Authorization", bearer("topic-user-none", null)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("ONBOARDING_NOT_COMPLETED"));
        mvc().perform(get("/api/topics/recommended").param("themeCode", "NOPE")
                        .header("Authorization", bearer("topic-user-bad", "LOVE")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_OPTION_CODE"));
    }
}
