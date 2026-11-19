package com.ganzithon.moyang.user.config;

import com.ganzithon.moyang.sheep.domain.SheepWallet;
import com.ganzithon.moyang.sheep.repository.SheepWalletRepository;
import com.ganzithon.moyang.user.repository.UserRepository;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 시연용 양털 채우기. 환경변수 DEMO_SHEEP_BALANCE(양수)가 켜졌을 때만, 시작할 때 demo1 ~ demoN 계정의 지갑을
 * 그 값까지 채운다. 이미 그 이상이면 건드리지 않는다(낮추지 않음). 서버를 다시 켜면 부족분이 다시 채워진다.
 * 다른 계정과 일반 사용자는 건드리지 않는다. 양털 내역(ledger)에는 남기지 않고 잔액만 맞춘다.
 *
 * sheep 패키지는 수정하지 않고, 이미 공개된 SheepWalletRepository와 SheepWallet.change만 쓴다.
 * 행사가 끝나면 DEMO_SHEEP_BALANCE를 지운다.
 */
@Component
@Order(2) // 계정을 만드는 DemoAccountSeeder(@Order(1))가 먼저 돈 뒤에 실행
public class DemoSheepSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoSheepSeeder.class);
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final UserRepository users;
    private final SheepWalletRepository wallets;
    private final DemoAccountProperties props;

    public DemoSheepSeeder(UserRepository users, SheepWalletRepository wallets, DemoAccountProperties props) {
        this.users = users;
        this.wallets = wallets;
        this.props = props;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        int target = props.demoSheepBalance();
        if (target <= 0) {
            return;
        }
        LocalDateTime now = LocalDateTime.now(KST);
        int filled = 0;
        for (int i = 1; i <= props.accountCount(); i++) {
            var user = users.findByLoginId("demo" + i);
            if (user.isEmpty()) {
                continue;
            }
            long userId = user.get().getId();
            wallets.ensureWallet(userId, now);
            SheepWallet wallet = wallets.lock(userId).orElseThrow();
            if (wallet.getBalance() < target) {
                wallet.change(target - wallet.getBalance(), now);
                filled++;
            }
        }
        log.info("데모 양털 시드: {}개 계정을 목표 잔액 {}개로 채웠어요.", filled, target);
    }
}
