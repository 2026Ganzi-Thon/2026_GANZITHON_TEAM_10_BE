package com.ganzithon.moyang.user.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 데모 계정 설정. 비밀번호는 코드에 두지 않고 환경변수(DEMO_PASSWORD)로만 받는다.
 * count: 만들 계정 수(demo1 ~ demoN), 환경변수 DEMO_ACCOUNT_COUNT, 기본 6.
 */
@ConfigurationProperties(prefix = "moyang.demo")
public record DemoAccountProperties(String password, Integer count) {
    public int accountCount() {
        return count == null ? 6 : count;
    }
}
