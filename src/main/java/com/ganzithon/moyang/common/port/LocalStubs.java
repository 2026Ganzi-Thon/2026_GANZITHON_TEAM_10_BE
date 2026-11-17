package com.ganzithon.moyang.common.port;

import com.ganzithon.moyang.common.exception.BusinessException;
import com.ganzithon.moyang.common.exception.ErrorCode;
import com.ganzithon.moyang.common.port.Ports.CurrentUserProvider;
import com.ganzithon.moyang.common.port.Ports.MeetingSharePort;
import com.ganzithon.moyang.common.port.Ports.TopicInfo;
import com.ganzithon.moyang.common.port.Ports.TopicReader;
import com.ganzithon.moyang.common.port.Ports.UserContext;
import com.ganzithon.moyang.common.port.Ports.UserContextProvider;
import java.util.Optional;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 로컬 개발용 가짜 구현. local 프로필에서만 켜진다 (--spring.profiles.active=local).
 * 진짜 구현(Auth=백승효, Sheep=박준석, Topic·Meeting=백승효)이 dev에 올라오면 해당 빈을 여기서 지운다.
 * X-User-Id 헤더를 그대로 믿으므로 배포 환경에서는 절대 켜면 안 된다. 그 외 프로필은 PortFallbacks(전부 막힘)를 쓴다.
 */
@Configuration
@Profile("local")
public class LocalStubs {

    @Bean
    CurrentUserProvider currentUserProvider() {
        return () -> {
            var attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            String header = attrs == null ? null : attrs.getRequest().getHeader("X-User-Id");
            try {
                return Long.parseLong(header);
            } catch (NumberFormatException e) {
                throw new BusinessException(ErrorCode.UNAUTHORIZED);
            }
        };
    }

    @Bean
    UserContextProvider userContextProvider() {
        return userId -> UserContext.EMPTY;
    }

    @Bean
    TopicReader topicReader() {
        return topicId -> Optional.of(new TopicInfo(topicId, "내가 서 있는 자리", "지금 내가 서 있는 곳을 그려 보세요."));
    }

    @Bean
    MeetingSharePort meetingSharePort() {
        return (drawingId, userId) -> false;
    }

    @Bean
    Ports.MeetingReader meetingReader() {
        return meetingId -> Optional.empty();
    }

    @Bean
    Ports.RecordPort recordPort() {
        // ponytail: DB 없는 메모리 번호. 재시작해도 drawings.record_id(UNIQUE)와 겹치지 않게 현재 시각(ms)에서 시작.
        // 준석님 Record 구현이 올라오면 이 빈은 지운다.
        java.util.concurrent.atomic.AtomicLong next = new java.util.concurrent.atomic.AtomicLong(System.currentTimeMillis());
        return new Ports.RecordPort() {
            public long create(long userId) {
                return next.incrementAndGet();
            }

            public void cardReady(long recordId) {
            }
        };
    }
}
