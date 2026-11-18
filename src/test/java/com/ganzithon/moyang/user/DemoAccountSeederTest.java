package com.ganzithon.moyang.user;

import static org.assertj.core.api.Assertions.assertThat;

import com.ganzithon.moyang.user.config.DemoAccountSeeder;
import com.ganzithon.moyang.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:user-seed;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "moyang.upload-dir=build/test-uploads",
        "moyang.demo.password=seed-test-pw-1"
})
class DemoAccountSeederTest {

    @Autowired UserRepository users;
    @Autowired PasswordEncoder encoder;
    @Autowired DemoAccountSeeder seeder;

    @Test
    void createsSixDemoAccountsByDefaultWithHashedPasswords() {
        for (int i = 1; i <= 6; i++) {
            var user = users.findByLoginId("demo" + i).orElseThrow();
            assertThat(user.getNickname()).isEqualTo("졸린 양 #" + i);
            assertThat(user.getPasswordHash()).isNotEqualTo("seed-test-pw-1");
            assertThat(encoder.matches("seed-test-pw-1", user.getPasswordHash())).isTrue();
            assertThat(user.isOnboardingCompleted()).isFalse();
        }
        assertThat(users.findByLoginId("demo7")).isEmpty();
    }

    @Test
    void runningAgainDoesNotDuplicateAccounts() {
        long before = users.count();
        seeder.run(null);
        assertThat(users.count()).isEqualTo(before);
    }
}
