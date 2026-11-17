package com.ganzithon.moyang.sheep;
import com.ganzithon.moyang.sheep.service.SheepService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.assertThat;
@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:sheep-demo;MODE=MySQL;DB_CLOSE_DELAY=-1","DEMO_DAILY_LIMIT_OFF=true"})
@ActiveProfiles("test")
class SheepDemoTest {
 @Autowired SheepService sheep;@Autowired PlatformTransactionManager manager;
 @Test void demoAwardsEachNewRecordButNeverRewardsRetries(){var tx=new TransactionTemplate(manager);for(long id=1;id<=3;id++){long record=id;assertThat(tx.execute(s->sheep.awardRecord(1,record)).sheepEarned()).isEqualTo(1);}assertThat(tx.execute(s->sheep.awardRecord(1,1)).sheepEarned()).isZero();assertThat(sheep.balance(1).balance()).isEqualTo(3);assertThat(sheep.transactions(1,null,20).items()).hasSize(3);}
}
