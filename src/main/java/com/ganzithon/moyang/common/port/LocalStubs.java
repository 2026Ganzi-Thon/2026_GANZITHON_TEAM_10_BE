package com.ganzithon.moyang.common.port;

import com.ganzithon.moyang.common.port.Ports.UserContext;
import com.ganzithon.moyang.common.port.Ports.UserContextProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * 로컬 개발용 가짜 구현. local 프로필에서만 켜진다 (--spring.profiles.active=local).
 * 진짜 구현(Auth·Topic·Meeting=백승효 완료, Record=박준석)이 dev에 올라오면 해당 빈을 여기서 지운다.
 * 로그인(CurrentUserProvider)은 진짜 JWT 구현(JwtCurrentUserProvider), 모임 포트(MeetingSharePort · MeetingReader)는
 * 진짜 구현(MeetingPortService)을 쓰므로 여기엔 없다. 그 외 프로필은 PortFallbacks(전부 막힘)를 쓴다.
 */
@Configuration
@Profile("local & !prod") // local과 prod를 같이 켜도 운영에서는 꺼진다
public class LocalStubs {

    @Bean
    UserContextProvider userContextProvider() {
        return userId -> UserContext.EMPTY;
    }

}
