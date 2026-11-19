package com.ganzithon.moyang.shape.controller;

import com.ganzithon.moyang.common.port.Ports.CurrentUserProvider;
import com.ganzithon.moyang.common.response.ApiResponse;
import com.ganzithon.moyang.shape.dto.ShapeDtos.ScheduleRequest;
import com.ganzithon.moyang.shape.dto.ShapeDtos.ShapeResponse;
import com.ganzithon.moyang.shape.service.ShapeService;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/shapes/me")
public class ShapeController {

    private final ShapeService shapes;
    private final CurrentUserProvider currentUser;

    public ShapeController(ShapeService shapes, CurrentUserProvider currentUser) {
        this.shapes = shapes;
        this.currentUser = currentUser;
    }

    @Operation(summary = "나의 모양 조회",
            description = "내 고민(themeCode · themeName), 끝낸 회차(completedRounds) / 전체 회차(totalRounds), 다음 회차(nextRound), "
                    + "정해 둔 활동 요일(weekdays), 다음 활동 날짜(nextActivityDate)를 돌려줍니다. "
                    + "고민을 아직 고르지 않았으면 data가 null입니다. "
                    + "completedRounds는 고민을 고른 뒤 저장한 회고 기록 수이고, 같은 고민으로 온보딩을 다시 저장해도 유지되지만 고민을 바꾸면 0부터 다시 셉니다. "
                    + "nextActivityDate는 고른 요일 중 오늘(한국 시간)부터 가장 가까운 날이고, 오늘 이미 기록을 저장했으면 내일부터 찾습니다. "
                    + "활동일을 놓쳐도 회차는 그대로이고 날짜만 다음 요일로 넘어갑니다. 모든 회차를 끝냈거나 요일을 정하지 않았으면 nextActivityDate가 null이고, "
                    + "모든 회차를 끝내면 nextRound도 null입니다.")
    @GetMapping
    public ApiResponse<ShapeResponse> me() {
        return ApiResponse.ok(shapes.me(currentUser.currentUserId()));
    }

    @Operation(summary = "나의 모양 활동 요일 저장",
            description = "활동할 요일을 보낸 값으로 통째로 덮어씁니다. weekdays는 MON · TUE · WED · THU · FRI · SAT · SUN 중 1~7개이고 중복은 안 됩니다. "
                    + "비어 있거나 null · 중복 · 없는 코드(소문자 포함)면 400 INVALID_REQUEST, 고민을 아직 고르지 않았으면 409 ONBOARDING_NOT_COMPLETED입니다. "
                    + "응답은 나의 모양 조회와 같은 모양이고 weekdays는 월→일 순서로 돌려줍니다.")
    @PutMapping("/schedule")
    public ApiResponse<ShapeResponse> saveSchedule(@RequestBody ScheduleRequest request) {
        return ApiResponse.ok(shapes.saveSchedule(currentUser.currentUserId(), request));
    }
}
