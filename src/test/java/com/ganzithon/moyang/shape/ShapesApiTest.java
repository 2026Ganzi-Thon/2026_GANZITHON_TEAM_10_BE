package com.ganzithon.moyang.shape;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ganzithon.moyang.auth.JwtTokenProvider;
import com.ganzithon.moyang.record.dto.RecordDtos.Analysis;
import com.ganzithon.moyang.record.dto.RecordDtos.Message;
import com.ganzithon.moyang.record.dto.RecordDtos.Snapshot;
import com.ganzithon.moyang.record.port.RecordSnapshotReader;
import com.ganzithon.moyang.record.service.RecordService;
import com.ganzithon.moyang.user.domain.User;
import com.ganzithon.moyang.user.repository.UserOnboardingRepository;
import com.ganzithon.moyang.user.repository.UserRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.TimeZone;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * 나의 모양. 시계를 고정해서 날짜 계산을 확인하고, 기록은 실제 RecordService로 저장해서
 * 고민 선택 시각(themeSelectedAt)과 기록 완료 시각(completedAt)이 같은 서울 시간 기준으로 비교되는지 본다.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:shapes-api;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "moyang.upload-dir=build/test-uploads"
})
@ActiveProfiles("test")
class ShapesApiTest {

    static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    /** 2026-11-18(수) 12:00 서울. */
    static final Instant WED_NOON = Instant.parse("2026-11-18T03:00:00Z");
    static final TimeZone ORIGINAL_ZONE = TimeZone.getDefault();

    @Autowired WebApplicationContext context;
    @Autowired JwtTokenProvider tokens;
    @Autowired UserRepository users;
    @Autowired UserOnboardingRepository onboardings;
    @Autowired RecordService records;
    @Autowired MutableClock clock;

    // JVM 기본 시간대가 서울이 아니어도 같은 결과가 나와야 한다(서버 설정에 기대지 않는다)
    @BeforeAll
    static void useForeignJvmZone() {
        TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles"));
    }

    @AfterAll
    static void restoreJvmZone() {
        TimeZone.setDefault(ORIGINAL_ZONE);
    }

    @BeforeEach
    void resetClock() {
        clock.set(WED_NOON);
    }

    MockMvc mvc() {
        return MockMvcBuilders.webAppContextSetup(context).build();
    }

    User newUser() {
        return users.save(new User("shape-" + UUID.randomUUID().toString().substring(0, 8), "x", "닉네임"));
    }

    String bearer(User u) {
        return "Bearer " + tokens.issue(u.getId());
    }

    ResultActions onboard(User u, String themeCode) throws Exception {
        return mvc().perform(put("/api/users/me/onboarding").header("Authorization", bearer(u))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"themeCode\":\"" + themeCode + "\"}")).andExpect(status().isOk());
    }

    ResultActions getShape(User u) throws Exception {
        return mvc().perform(get("/api/shapes/me").header("Authorization", bearer(u)));
    }

    ResultActions putSchedule(User u, String json) throws Exception {
        return mvc().perform(put("/api/shapes/me/schedule").header("Authorization", bearer(u))
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    ResultActions schedule(User u, String... days) throws Exception {
        String list = String.join(",", java.util.Arrays.stream(days).map(d -> "\"" + d + "\"").toList());
        return putSchedule(u, "{\"weekdays\":[" + list + "]}").andExpect(status().isOk());
    }

    /** 지금 시계 시각에 기록 하나를 저장 완료한다(RecordService 그대로). */
    void saveRecord(User u) {
        long id = records.create(u.getId());
        records.cardReady(id);
        records.complete(u.getId(), id, "오늘의 회고");
    }

    void expectError(ResultActions r, int status, String code) throws Exception {
        r.andExpect(status().is(status)).andExpect(jsonPath("$.error.code").value(code));
    }

    // ── 조회 ──

    @Test
    void returnsNullBeforeAConcernIsChosen() throws Exception {
        getShape(newUser()).andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").value(nullValue()));
    }

    @Test
    void afterOnboardingItStartsAtRoundOne() throws Exception {
        User u = newUser();
        onboard(u, "RELATION_FAMILY");
        getShape(u).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.themeCode").value("RELATION_FAMILY"))
                .andExpect(jsonPath("$.data.themeName").value("인간관계·가족"))
                .andExpect(jsonPath("$.data.completedRounds").value(0))
                .andExpect(jsonPath("$.data.totalRounds").value(2))
                .andExpect(jsonPath("$.data.nextRound").value(1))
                .andExpect(jsonPath("$.data.weekdays.length()").value(0))
                .andExpect(jsonPath("$.data.nextActivityDate").value(nullValue()));
    }

    @Test
    void directConcernShowsTheUsersOwnText() throws Exception {
        User u = newUser();
        mvc().perform(put("/api/users/me/onboarding").header("Authorization", bearer(u))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"themeCode\":\"DIRECT\",\"themeText\":\"이직이 고민이에요\"}")).andExpect(status().isOk());
        getShape(u).andExpect(jsonPath("$.data.themeName").value("이직이 고민이에요"));
    }

    @Test
    void requiresAToken() throws Exception {
        expectError(mvc().perform(get("/api/shapes/me")), 401, "UNAUTHORIZED");
        expectError(mvc().perform(put("/api/shapes/me/schedule")
                .contentType(MediaType.APPLICATION_JSON).content("{\"weekdays\":[\"WED\"]}")), 401, "UNAUTHORIZED");
    }

    // ── 완료 회차: 고민 선택 시각 vs 기록 완료 시각 ──

    @Test
    void aRecordSavedRightAfterOnboardingCountsAsRoundOne() throws Exception {
        User u = newUser();
        onboard(u, "RELATION_FAMILY");
        clock.advance(Duration.ofSeconds(30));
        saveRecord(u);
        getShape(u).andExpect(jsonPath("$.data.completedRounds").value(1))
                .andExpect(jsonPath("$.data.nextRound").value(2));
    }

    @Test
    void aRecordSavedAtTheSameInstantAsTheConcernAlsoCounts() throws Exception {
        User u = newUser();
        onboard(u, "LOVE");
        saveRecord(u); // 시계가 그대로라서 completedAt == themeSelectedAt
        getShape(u).andExpect(jsonPath("$.data.completedRounds").value(1));
    }

    @Test
    void bothTimesAreStoredAsSeoulTimeRegardlessOfTheJvmZone() throws Exception {
        User u = newUser();
        onboard(u, "LOVE");
        LocalDateTime expected = LocalDateTime.ofInstant(WED_NOON, SEOUL); // 2026-11-18T12:00
        assertThat(onboardings.findById(u.getId()).orElseThrow().getThemeSelectedAt()).isEqualTo(expected);
        saveRecord(u);
        assertThat(records.today(u.getId()).recordDate()).isEqualTo(expected.toLocalDate());
        assertThat(records.list(u.getId(), null, null, 10).items().get(0).completedAt().toLocalDateTime())
                .isEqualTo(expected);
    }

    @Test
    void aRecordSavedBeforeTheConcernWasChosenIsNotCounted() throws Exception {
        User u = newUser();
        saveRecord(u);
        clock.advance(Duration.ofMinutes(5));
        onboard(u, "LOVE");
        getShape(u).andExpect(jsonPath("$.data.completedRounds").value(0));
    }

    @Test
    void savingTheSameConcernAgainKeepsTheRoundsButChangingItStartsOver() throws Exception {
        User u = newUser();
        onboard(u, "LOVE");
        clock.advance(Duration.ofMinutes(1));
        saveRecord(u);
        clock.advance(Duration.ofMinutes(1));
        onboard(u, "LOVE");
        getShape(u).andExpect(jsonPath("$.data.completedRounds").value(1));

        clock.advance(Duration.ofMinutes(1));
        onboard(u, "MONEY");
        getShape(u).andExpect(jsonPath("$.data.themeCode").value("MONEY"))
                .andExpect(jsonPath("$.data.completedRounds").value(0))
                .andExpect(jsonPath("$.data.nextRound").value(1));
    }

    @Test
    void finishingAllRoundsClearsTheNextRoundAndDate() throws Exception {
        User u = newUser();
        onboard(u, "LOVE");
        schedule(u, "WED");
        clock.advance(Duration.ofMinutes(1));
        saveRecord(u);
        saveRecord(u);
        getShape(u).andExpect(jsonPath("$.data.completedRounds").value(2))
                .andExpect(jsonPath("$.data.nextRound").value(nullValue()))
                .andExpect(jsonPath("$.data.nextActivityDate").value(nullValue()))
                .andExpect(jsonPath("$.data.weekdays[0]").value("WED"));
        saveRecord(u);
        getShape(u).andExpect(jsonPath("$.data.completedRounds").value(2)); // 전체 회차를 넘지 않는다
    }

    // ── 활동 요일 저장 ──

    @Test
    void savedWeekdaysComeBackSortedAndCanBeOverwritten() throws Exception {
        User u = newUser();
        onboard(u, "LOVE");
        schedule(u, "WED", "MON").andExpect(jsonPath("$.data.weekdays[0]").value("MON"))
                .andExpect(jsonPath("$.data.weekdays[1]").value("WED"));
        getShape(u).andExpect(jsonPath("$.data.weekdays.length()").value(2));
        schedule(u, "SUN").andExpect(jsonPath("$.data.weekdays.length()").value(1));
        getShape(u).andExpect(jsonPath("$.data.weekdays[0]").value("SUN"));
    }

    @Test
    void scheduleBeforeAConcernIsChosenIs409() throws Exception {
        expectError(putSchedule(newUser(), "{\"weekdays\":[\"WED\"]}"), 409, "ONBOARDING_NOT_COMPLETED");
    }

    @Test
    void badWeekdaysAre400() throws Exception {
        User u = newUser();
        onboard(u, "LOVE");
        for (String body : List.of(
                "{}", "{\"weekdays\":null}", "{\"weekdays\":[]}", "{\"weekdays\":[null]}",
                "{\"weekdays\":[\"WED\",\"WED\"]}", "{\"weekdays\":[\"FUNDAY\"]}", "{\"weekdays\":[\"wed\"]}",
                "{\"weekdays\":[\"WEDNESDAY\"]}", "{\"weekdays\":[\"MON\",\"TUE\",\"WED\",\"THU\",\"FRI\",\"SAT\",\"SUN\",\"MON\"]}")) {
            expectError(putSchedule(u, body), 400, "INVALID_REQUEST");
        }
        getShape(u).andExpect(jsonPath("$.data.weekdays.length()").value(0)); // 실패한 요청은 저장되지 않는다
    }

    @Test
    void formatErrorsComeBeforeTheOnboardingCheck() throws Exception {
        expectError(putSchedule(newUser(), "{\"weekdays\":[]}"), 400, "INVALID_REQUEST");
    }

    // ── 다음 활동 날짜 ──

    @Test
    void todayCountsWhenItIsAChosenDayAndNothingWasSavedToday() throws Exception {
        User u = newUser();
        onboard(u, "LOVE");
        schedule(u, "WED").andExpect(jsonPath("$.data.nextActivityDate").value("2026-11-18"));
    }

    @Test
    void afterSavingARecordTodayTheNextDateStartsTomorrow() throws Exception {
        User u = newUser();
        onboard(u, "LOVE");
        schedule(u, "WED");
        clock.advance(Duration.ofMinutes(1));
        saveRecord(u);
        getShape(u).andExpect(jsonPath("$.data.nextActivityDate").value("2026-11-25"));
        schedule(u, "MON", "WED").andExpect(jsonPath("$.data.nextActivityDate").value("2026-11-23"));
        schedule(u, "WED", "THU").andExpect(jsonPath("$.data.nextActivityDate").value("2026-11-19"));
    }

    @Test
    void aMissedDayKeepsTheRoundAndJustMovesTheDate() throws Exception {
        User u = newUser();
        onboard(u, "LOVE");
        schedule(u, "WED");
        clock.advance(Duration.ofDays(1)); // 목요일, 수요일 활동을 놓침
        getShape(u).andExpect(jsonPath("$.data.completedRounds").value(0))
                .andExpect(jsonPath("$.data.nextRound").value(1))
                .andExpect(jsonPath("$.data.nextActivityDate").value("2026-11-25"));
    }

    @Test
    void theDateFollowsSeoulMidnightNotUtc() throws Exception {
        User u = newUser();
        onboard(u, "LOVE");
        schedule(u, "WED");
        // UTC로는 아직 수요일 15:30이지만 서울은 목요일 00:30
        clock.set(Instant.parse("2026-11-18T15:30:00Z"));
        getShape(u).andExpect(jsonPath("$.data.nextActivityDate").value("2026-11-25"));
    }

    // ── 테스트용 시계 · 기록 스냅샷 ──

    static class MutableClock extends Clock {
        private final AtomicReference<Instant> now = new AtomicReference<>(WED_NOON);

        void set(Instant instant) { now.set(instant); }
        void advance(Duration d) { now.updateAndGet(i -> i.plus(d)); }

        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now.get(); }
    }

    @TestConfiguration
    static class Fixtures {
        @Bean @Primary MutableClock mutableClock() {
            return new MutableClock();
        }

        @Bean @Primary RecordSnapshotReader snapshotReader() {
            return id -> Optional.of(new Snapshot(id, "url", null, new Analysis("a", "b", "c", "d"),
                    List.of(new Message(1, "USER", "hello", OffsetDateTime.parse("2026-11-18T11:58:00+09:00")))));
        }
    }
}
