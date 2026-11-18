package com.ganzithon.moyang.record;
import com.ganzithon.moyang.record.service.RecordService;
import com.ganzithon.moyang.record.dto.RecordDtos.*;
import com.ganzithon.moyang.record.port.RecordSnapshotReader;
import com.ganzithon.moyang.record.domain.ReflectionRecord;
import com.ganzithon.moyang.record.repository.RecordRepository;
import com.ganzithon.moyang.sheep.service.SheepService;
import com.ganzithon.moyang.sheep.domain.SheepWallet;
import com.ganzithon.moyang.sheep.repository.SheepWalletRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.*;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.ActiveProfiles;
import java.time.OffsetDateTime;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;
@SpringBootTest(classes=RecordSchemaTest.SchemaConfig.class,properties={
 "spring.datasource.url=jdbc:h2:mem:record-schema;MODE=MySQL;DB_CLOSE_DELAY=-1",
 "spring.sql.init.mode=always",
 "spring.sql.init.schema-locations=classpath:sheep-h2-schema.sql,classpath:records-h2-schema.sql",
 "spring.jpa.hibernate.ddl-auto=validate"})
@ActiveProfiles({"test","records-schema-only"})
class RecordSchemaTest {
 @Autowired RecordService records;
 @Test void suppliedSqlMapsRecordMessagesAndReward(){
  long id=records.create(88);records.cardReady(id);
  assertThat(records.complete(88,id,"직접 입력한 제목").sheepEarned()).isEqualTo(1);
  assertThat(records.detail(88,id).messages()).hasSize(1);
 }
 @Configuration(proxyBeanMethods=false) @Profile("records-schema-only")
 @EnableAutoConfiguration
 @EntityScan(basePackageClasses={ReflectionRecord.class,SheepWallet.class})
 @EnableJpaRepositories(basePackageClasses={RecordRepository.class,SheepWalletRepository.class})
 @Import({RecordService.class,SheepService.class,com.ganzithon.moyang.sheep.config.SheepConfiguration.class})
 static class SchemaConfig {
  @Bean RecordSnapshotReader reader(){return id->Optional.of(new Snapshot(id,"url",null,new Analysis("a","b","c","d"),List.of(new Message(1,"USER","hello",OffsetDateTime.now()))));}
 }
}
