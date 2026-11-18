package com.ganzithon.moyang.user.config;

import com.ganzithon.moyang.user.domain.User;
import com.ganzithon.moyang.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * 시작할 때 데모 계정(demo1 ~ demoN)이 없으면 만든다. 이미 있으면 비밀번호만 현재 환경변수 값으로 맞춘다.
 * DEMO_PASSWORD가 비어 있으면 아무것도 만들지 않는다 (코드에 기본 비밀번호를 두지 않기 위해).
 */
@Component
@EnableConfigurationProperties(DemoAccountProperties.class)
public class DemoAccountSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoAccountSeeder.class);
    private static final String NICKNAME_PREFIX = "졸린 양";

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final DemoAccountProperties props;

    public DemoAccountSeeder(UserRepository users, PasswordEncoder encoder, DemoAccountProperties props) {
        this.users = users;
        this.encoder = encoder;
        this.props = props;
    }

    @Override
    public void run(ApplicationArguments args) {
        String password = props.password();
        if (password == null || password.isBlank()) {
            log.warn("DEMO_PASSWORD가 없어 데모 계정을 만들지 않았어요. 로그인은 불가능해요.");
            return;
        }
        int count = props.accountCount();
        for (int i = 1; i <= count; i++) {
            String loginId = "demo" + i;
            var existing = users.findByLoginId(loginId);
            if (existing.isEmpty()) {
                users.save(new User(loginId, encoder.encode(password), NICKNAME_PREFIX + " #" + i));
            } else if (!encoder.matches(password, existing.get().getPasswordHash())) {
                existing.get().changePasswordHash(encoder.encode(password));
                users.save(existing.get());
            }
        }
        log.info("데모 계정 {}개 준비 완료 (demo1 ~ demo{})", count, count);
    }
}
