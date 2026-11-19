package com.ganzithon.moyang.shape.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 나의 모양 활동 요일. 사용자마다 한 줄(userId가 PK)이고 다시 저장하면 통째로 덮어쓴다.
 * 다음 활동 날짜는 저장하지 않고 요청할 때마다 계산한다(놓친 날은 자동으로 다음 요일로 넘어간다).
 * 다른 도메인처럼 users 테이블과 FK는 두지 않고 userId 숫자만 가진다.
 */
@Entity
@Table(name = "user_shape_schedules")
public class ShapeSchedule {

    private static final String SEPARATOR = ",";

    @Id
    private Long userId;

    /** 요일 코드(MON~SUN)를 월→일 순서로 쉼표로 이은 문자열. */
    @Column(nullable = false, length = 30)
    private String weekdays;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    protected ShapeSchedule() {}

    public ShapeSchedule(Long userId) {
        this.userId = userId;
    }

    public void overwrite(Set<DayOfWeek> days, LocalDateTime now) {
        this.weekdays = EnumSet.copyOf(days).stream().map(ShapeSchedule::code).collect(Collectors.joining(SEPARATOR));
        this.updatedAt = now;
    }

    public Long getUserId() { return userId; }

    /** 월→일 순서로 정렬된 요일 코드. */
    public List<String> getWeekdayCodes() {
        return weekdays == null || weekdays.isBlank() ? List.of() : Arrays.asList(weekdays.split(SEPARATOR));
    }

    public Set<DayOfWeek> getDays() {
        Set<DayOfWeek> days = EnumSet.noneOf(DayOfWeek.class);
        getWeekdayCodes().forEach(c -> days.add(fromCode(c)));
        return days;
    }

    /** MON, TUE ... 처럼 앞 세 글자 대문자. */
    public static String code(DayOfWeek day) {
        return day.name().substring(0, 3);
    }

    /** 코드를 요일로 바꾼다. 없는 코드면 null. */
    public static DayOfWeek parse(String code) {
        for (DayOfWeek d : DayOfWeek.values()) {
            if (code(d).equals(code)) {
                return d;
            }
        }
        return null;
    }

    private static DayOfWeek fromCode(String code) {
        DayOfWeek d = parse(code);
        if (d == null) {
            throw new IllegalStateException("잘못 저장된 요일 코드: " + code);
        }
        return d;
    }
}
