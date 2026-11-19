package com.ganzithon.moyang.shape.dto;

import java.time.LocalDate;
import java.util.List;

public final class ShapeDtos {
    private ShapeDtos() {}

    /** 활동 요일 저장 요청. 요일 코드는 MON, TUE, WED, THU, FRI, SAT, SUN. */
    public record ScheduleRequest(List<String> weekdays) {}

    /**
     * 나의 모양. nextRound와 nextActivityDate는 모든 회차를 끝냈으면 null이고,
     * nextActivityDate는 요일을 아직 정하지 않았어도 null이다.
     */
    public record ShapeResponse(
            String themeCode,
            String themeName,
            int completedRounds,
            int totalRounds,
            Integer nextRound,
            List<String> weekdays,
            LocalDate nextActivityDate) {}
}
