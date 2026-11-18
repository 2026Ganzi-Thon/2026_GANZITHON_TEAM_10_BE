package com.ganzithon.moyang.meeting.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 예약 모임을 시작하거나 자동 취소하는 스케줄러. 일정 간격으로 {@link #runOnce()}를 부른다.
 * 판단은 모두 DB의 시각과 입장 기록으로 하므로, 서버를 다시 켜거나 스케줄러를 새로 만들어도 이미 처리된 모임은 건드리지 않는다.
 * `moyang.meeting.scheduler.enabled=false`로 끌 수 있다(테스트에서 사용).
 */
@Component
@EnableScheduling
@ConditionalOnProperty(name = "moyang.meeting.scheduler.enabled", havingValue = "true", matchIfMissing = true)
public class MeetingScheduler {

    private final MeetingLifecycleService lifecycle;
    private final MeetingQueryService time;

    public MeetingScheduler(MeetingLifecycleService lifecycle, MeetingQueryService time) {
        this.lifecycle = lifecycle;
        this.time = time;
    }

    @Scheduled(fixedDelayString = "${moyang.meeting.scheduler.interval-ms:10000}")
    public void runOnce() {
        lifecycle.processDue(time.now());
    }
}
