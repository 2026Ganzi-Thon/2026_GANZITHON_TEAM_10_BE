package com.ganzithon.moyang.sheep;
import com.ganzithon.moyang.sheep.service.SheepService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.*;
@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:sheep-schema;MODE=MySQL;DB_CLOSE_DELAY=-1","spring.sql.init.mode=always","spring.sql.init.schema-locations=classpath:sheep-h2-schema.sql","spring.jpa.hibernate.ddl-auto=validate"})
@ActiveProfiles("test")
class SheepSchemaTest {
 @Autowired SheepService sheep;@Autowired JdbcTemplate jdbc;@Autowired PlatformTransactionManager manager;
 @Test void schemaSupportsZeroDecisionsAndFirstOnlyRewards(){var tx=new TransactionTemplate(manager);assertThat(tx.execute(s->sheep.awardRecord(1,11)).sheepEarned()).isEqualTo(1);assertThat(tx.execute(s->sheep.awardRecord(1,12)).sheepEarned()).isZero();}
 @Test void databaseRejectsNegativeWalletAndInvalidMeetingEntry(){new TransactionTemplate(manager).execute(s->sheep.awardRecord(2,21));assertThatThrownBy(()->jdbc.update("update sheep_wallets set balance=-1 where user_id=2")).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);assertThatThrownBy(()->jdbc.update("insert into sheep_transactions(user_id,transaction_type,reference_id,meeting_id,amount,balance_after,reward_mode,reward_date,created_at) values(2,'MEETING_JOIN','55',NULL,-1,0,'NONE',NULL,CURRENT_TIMESTAMP)")).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);}
}
