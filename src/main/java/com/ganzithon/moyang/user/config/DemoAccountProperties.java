package com.ganzithon.moyang.user.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 데모 계정 설정. 비밀번호는 코드에 두지 않고 환경변수(DEMO_PASSWORD)로만 받는다.
 * count: 만들 계정 수(demo1 ~ demoN), 환경변수 DEMO_ACCOUNT_COUNT, 기본 6.
 * sheepBalance: 시연용 양털 목표 잔액, 환경변수 DEMO_SHEEP_BALANCE. 없거나 0이면 쓰지 않는다.
 */
@ConfigurationProperties(prefix = "moyang.demo")
public record DemoAccountProperties(String password, Integer count, Integer sheepBalance) {
    public int accountCount() {
        return count == null ? 6 : count;
    }

    public int demoSheepBalance() {
        return sheepBalance == null ? 0 : Math.max(sheepBalance, 0);
    }
}
