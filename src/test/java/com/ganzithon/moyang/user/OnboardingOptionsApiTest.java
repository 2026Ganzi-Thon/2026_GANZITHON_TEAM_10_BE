package com.ganzithon.moyang.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ganzithon.moyang.user.domain.OnboardingOptions;
import com.ganzithon.moyang.user.domain.OnboardingOptions.Option;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:onboarding-options-api;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "moyang.upload-dir=build/test-uploads"
})
class OnboardingOptionsApiTest {

    static final String URL = "/api/onboarding/options";

    @Autowired WebApplicationContext context;

    MockMvc mvc() {
        return MockMvcBuilders.webAppContextSetup(context).build();
    }

    @Test
    void worksWithoutTokenAndHasFourLists() throws Exception {
        mvc().perform(get(URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.themes.length()").value(8))
                .andExpect(jsonPath("$.data.emotions.length()").value(10))
                .andExpect(jsonPath("$.data.lifeStatuses.length()").value(8))
                .andExpect(jsonPath("$.data.artExperiences.length()").value(5));
    }

    @Test
    void ordersAndLabelsMatchTheNotionTable() throws Exception {
        mvc().perform(get(URL))
                .andExpect(jsonPath("$.data.themes[0].code").value("STUDY_CAREER"))
                .andExpect(jsonPath("$.data.themes[0].label").value("학업·진로·일"))
                .andExpect(jsonPath("$.data.themes[5].code").value("RELATION_FAMILY"))
                .andExpect(jsonPath("$.data.themes[5].label").value("인간관계·가족"))
                .andExpect(jsonPath("$.data.themes[7].code").value("DIRECT"))
                .andExpect(jsonPath("$.data.emotions[0].label").value("불안해요"))
                .andExpect(jsonPath("$.data.emotions[9].code").value("DIRECT"))
                .andExpect(jsonPath("$.data.emotions[9].label").value("직접 말할래요"))
                .andExpect(jsonPath("$.data.lifeStatuses[7].code").value("ETC"))
                .andExpect(jsonPath("$.data.artExperiences[4].code").value("PRO"))
                .andExpect(jsonPath("$.data.artExperiences[4].label").value("전문 경험"));
    }

    @Test
    void everyListedCodeIsAcceptedByTheSavingValidators() {
        List<Option> themes = OnboardingOptions.THEMES;
        assertThat(themes).allMatch(o -> OnboardingOptions.isTheme(o.code()));
        assertThat(OnboardingOptions.EMOTIONS).allMatch(o -> OnboardingOptions.isEmotion(o.code()));
        assertThat(OnboardingOptions.LIFE_STATUSES).allMatch(o -> OnboardingOptions.isLifeStatus(o.code()));
        assertThat(OnboardingOptions.ART_EXPERIENCES).allMatch(o -> OnboardingOptions.isArtExperience(o.code()));
        assertThat(OnboardingOptions.EMOTIONS).extracting(Option::code).contains(OnboardingOptions.EMOTION_DIRECT);
        assertThat(OnboardingOptions.LIFE_STATUSES).extracting(Option::code).contains(OnboardingOptions.LIFE_STATUS_ETC);
    }

    @Test
    void anInvalidTokenDoesNotBreakThePublicEndpoint() throws Exception {
        mvc().perform(get(URL).header("Authorization", "Bearer not-a-real-token"))
                .andExpect(status().isOk());
    }
}
