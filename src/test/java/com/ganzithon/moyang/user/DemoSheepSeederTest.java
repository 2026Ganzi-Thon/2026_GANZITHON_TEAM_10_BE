package com.ganzithon.moyang.user;

import static org.assertj.core.api.Assertions.assertThat;

import com.ganzithon.moyang.sheep.repository.SheepWalletRepository;
import com.ganzithon.moyang.sheep.service.SheepService;
import com.ganzithon.moyang.user.config.DemoSheepSeeder;
import com.ganzithon.moyang.user.domain.User;
import com.ganzithon.moyang.user.repository.UserRepository;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** DEMO_SHEEP_BALANCE가 켜졌을 때: demo1~demoN 지갑만 목표 잔액까지 채우고, 많으면 그대로, 재시작하면 부족분을 다시 채운다. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:demo-sheep-on;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "moyang.upload-dir=build/test-uploads",
        "moyang.demo.password=sheep-seed-pw-1",
        "moyang.demo.count=3",
        "moyang.demo.sheep-balance=10"
})
class DemoSheepSeederTest {

    @Autowired UserRepository users;
    @Autowired SheepWalletRepository wallets;
    @Autowired SheepService sheep;
    @Autowired DemoSheepSeeder seeder;
    @Autowired PlatformTransactionManager tx;

    long idOf(String loginId) {
        return users.findByLoginId(loginId).orElseThrow().getId();
    }

    int balanceOf(String loginId) {
        return sheep.balance(idOf(loginId)).balance();
    }

    void changeBalance(String loginId, int delta) {
        new TransactionTemplate(tx).executeWithoutResult(
                s -> wallets.lock(idOf(loginId)).orElseThrow().change(delta, LocalDateTime.now()));
    }

    @Test
    void startupFillsOnlyDemoAccountsUpToTargetAfterAccountsExist() {
        // 앱이 뜬 직후: 계정(@Order 1)이 먼저 만들어졌고 그 지갑(@Order 2)이 채워져 있다
        assertThat(balanceOf("demo1")).isEqualTo(10);
        assertThat(balanceOf("demo2")).isEqualTo(10);
        assertThat(balanceOf("demo3")).isEqualTo(10);
        // 대상은 demo1~demoN뿐: 개수 밖 계정과 일반 사용자는 그대로 0
        assertThat(users.findByLoginId("demo4")).isEmpty();
        User other = users.save(new User("not-demo-user", "x", "일반 사용자"));
        seeder.run(null);
        assertThat(sheep.balance(other.getId()).balance()).isZero();
    }

    @Test
    void doesNotLowerAnAlreadyHigherBalance() {
        changeBalance("demo1", 5); // 15
        seeder.run(null);
        assertThat(balanceOf("demo1")).isEqualTo(15);
        changeBalance("demo1", -5); // 다른 테스트에 영향이 없도록 10으로 되돌림
    }

    @Test
    void restartRefillsOnlyTheShortfall() {
        changeBalance("demo2", -4); // 시연 중 양털을 써서 6이 됨
        assertThat(balanceOf("demo2")).isEqualTo(6);
        seeder.run(null); // 서버를 다시 켠 것과 같다
        assertThat(balanceOf("demo2")).isEqualTo(10);
        assertThat(balanceOf("demo3")).isEqualTo(10);
    }
}
