package com.ganzithon.moyang.shape.service;

import com.ganzithon.moyang.common.exception.BusinessException;
import com.ganzithon.moyang.common.exception.ErrorCode;
import com.ganzithon.moyang.shape.domain.ShapeSchedule;
import com.ganzithon.moyang.shape.dto.ShapeDtos.ScheduleRequest;
import com.ganzithon.moyang.shape.dto.ShapeDtos.ShapeResponse;
import com.ganzithon.moyang.shape.repository.ShapeRecordCounter;
import com.ganzithon.moyang.shape.repository.ShapeScheduleRepository;
import com.ganzithon.moyang.user.domain.OnboardingOptions;
import com.ganzithon.moyang.user.domain.UserOnboarding;
import com.ganzithon.moyang.user.repository.UserOnboardingRepository;
import com.ganzithon.moyang.user.repository.UserRepository;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 나의 모양: 같은 고민으로 이어 가는 혼자 하는 프로그램의 진행 상황과 다음 활동 날짜.
 * 완료 회차 = 고민을 고른 뒤에 저장된 기록 수(회고 저장 API는 건드리지 않고 읽기만 한다).
 * 시간은 기록 서비스와 같은 Clock + 서울 시간으로 비교한다.
 */
@Service
public class ShapeService {

    /** 이번 범위는 두 번째 모양까지(4차 회의). 늘리려면 이 값만 바꾼다. */
    static final int TOTAL_ROUNDS = 2;
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final String THEME_DIRECT = "DIRECT";

    private final UserRepository users;
    private final UserOnboardingRepository onboardings;
    private final ShapeScheduleRepository schedules;
    private final ShapeRecordCounter records;
    private final Clock clock;

    public ShapeService(UserRepository users, UserOnboardingRepository onboardings,
                        ShapeScheduleRepository schedules, ShapeRecordCounter records, Clock clock) {
        this.users = users;
        this.onboardings = onboardings;
        this.schedules = schedules;
        this.records = records;
        this.clock = clock;
    }

    /** 고민을 아직 고르지 않았으면 null. */
    @Transactional(readOnly = true)
    public ShapeResponse me(long userId) {
        UserOnboarding onboarding = onboardings.findById(userId).orElse(null);
        if (onboarding == null) {
            return null;
        }
        return build(userId, onboarding, schedules.findById(userId).orElse(null));
    }

    /** 검사 순서: 형식(INVALID_REQUEST) → 고민 선택 전(ONBOARDING_NOT_COMPLETED). */
    @Transactional
    public ShapeResponse saveSchedule(long userId, ScheduleRequest req) {
        Set<DayOfWeek> days = validate(req);

        // 같은 사용자의 동시 저장이 PK 충돌로 실패하지 않도록 사용자 행을 먼저 잠근다
        users.findByIdForUpdate(userId).orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
        UserOnboarding onboarding = onboardings.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ONBOARDING_NOT_COMPLETED));
        ShapeSchedule schedule = schedules.findById(userId).orElseGet(() -> new ShapeSchedule(userId));
        schedule.overwrite(days, now());
        schedules.save(schedule);
        return build(userId, onboarding, schedule);
    }

    private ShapeResponse build(long userId, UserOnboarding onboarding, ShapeSchedule schedule) {
        LocalDateTime since = onboarding.getThemeSelectedAt();
        int completed = (int) Math.min(records.countCompletedSince(userId, since), TOTAL_ROUNDS);
        Integer nextRound = completed < TOTAL_ROUNDS ? completed + 1 : null;

        LocalDate nextDate = null;
        if (nextRound != null && schedule != null) {
            LocalDate today = now().toLocalDate();
            // 오늘 이미 기록을 저장했으면 오늘 몫은 끝난 것이라 내일부터 찾는다
            LocalDate start = records.existsCompletedOn(userId, today, since) ? today.plusDays(1) : today;
            nextDate = nextActivityDate(schedule.getDays(), start);
        }
        return new ShapeResponse(onboarding.getThemeCode(), themeName(onboarding), completed, TOTAL_ROUNDS,
                nextRound, schedule == null ? List.of() : schedule.getWeekdayCodes(), nextDate);
    }

    /** start부터 7일 안에서 고른 요일에 해당하는 가장 가까운 날. 고른 요일이 없으면 null. */
    static LocalDate nextActivityDate(Set<DayOfWeek> days, LocalDate start) {
        if (days.isEmpty()) {
            return null;
        }
        for (int i = 0; i < 7; i++) {
            LocalDate d = start.plusDays(i);
            if (days.contains(d.getDayOfWeek())) {
                return d;
            }
        }
        return null;
    }

    private static String themeName(UserOnboarding o) {
        if (THEME_DIRECT.equals(o.getThemeCode()) && o.getThemeText() != null && !o.getThemeText().isBlank()) {
            return o.getThemeText();
        }
        return OnboardingOptions.THEMES.stream().filter(t -> t.code().equals(o.getThemeCode()))
                .map(OnboardingOptions.Option::label).findFirst().orElse(o.getThemeCode());
    }

    private static Set<DayOfWeek> validate(ScheduleRequest req) {
        List<String> codes = req == null ? null : req.weekdays();
        if (codes == null || codes.isEmpty() || codes.size() > 7) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        Set<DayOfWeek> days = EnumSet.noneOf(DayOfWeek.class);
        for (String code : codes) {
            DayOfWeek day = code == null ? null : ShapeSchedule.parse(code);
            if (day == null || !days.add(day)) {
                throw new BusinessException(ErrorCode.INVALID_REQUEST);
            }
        }
        return days;
    }

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(clock.instant(), SEOUL);
    }
}
