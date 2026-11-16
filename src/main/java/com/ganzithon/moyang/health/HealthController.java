package com.ganzithon.moyang.health;

import com.ganzithon.moyang.common.response.ApiResponse;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 배포 확인용. serverTime이 +09:00(KST)으로 나오면 타임존 설정이 살아 있는 것이다.
 */
@RestController
public class HealthController {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    @GetMapping("/health")
    public ApiResponse<Map<String, String>> health() {
        return ApiResponse.ok(Map.of(
                "status", "UP",
                "serverTime", OffsetDateTime.now(SEOUL).toString()));
    }
}
