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
    private final MeetingPurgeService purge;

    public MeetingScheduler(MeetingLifecycleService lifecycle, MeetingQueryService time, MeetingPurgeService purge) {
        this.lifecycle = lifecycle;
        this.time = time;
        this.purge = purge;
    }

    @Scheduled(fixedDelayString = "${moyang.meeting.scheduler.interval-ms:10000}")
    public void runOnce() {
        lifecycle.processDue(time.now());
    }

    /** 종료 후 24시간이 지난 모임의 채팅을 지운다. 급하지 않아서 10분마다 돈다(서버를 켠 1분 뒤에 첫 실행). */
    @Scheduled(initialDelayString = "${moyang.meeting.purge.initial-delay-ms:60000}",
            fixedDelayString = "${moyang.meeting.purge.interval-ms:600000}")
    public void purgeOnce() {
        purge.purgeExpired(time.now());
    }
}
