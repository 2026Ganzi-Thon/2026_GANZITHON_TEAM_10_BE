package com.ganzithon.moyang.record;

import com.ganzithon.moyang.record.service.RecordService;
import com.ganzithon.moyang.record.port.RecordSnapshotReader;
import com.ganzithon.moyang.record.dto.RecordDtos.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.*;
import org.springframework.context.annotation.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.jdbc.core.JdbcTemplate;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties="spring.datasource.url=jdbc:h2:mem:record-midnight;MODE=MySQL;DB_CLOSE_DELAY=-1")
@ActiveProfiles("test") @Import(RecordDayBoundaryTest.Config.class)
class RecordDayBoundaryTest {
 @Autowired RecordService records; @Autowired BoundaryClock clock; @Autowired JdbcTemplate jdbc;
 @Test void rewardDayMatchesCompletionEvenWhenWalletOperationCrossesMidnight(){
  long id=records.create(777);records.cardReady(id);clock.arm();
  var saved=records.complete(777,id);
  assertThat(saved.recordDate()).isEqualTo(LocalDate.of(2026,11,4));
  assertThat(jdbc.queryForObject("select reward_date from sheep_transactions where user_id=777",LocalDate.class)).isEqualTo(saved.recordDate());
  long next=records.create(777);records.cardReady(next);
  assertThat(records.complete(777,next).sheepEarned()).isEqualTo(1);
 }
 static class BoundaryClock extends Clock {
  final AtomicInteger calls=new AtomicInteger();boolean armed;
  void arm(){armed=true;calls.set(0);}
  public ZoneId getZone(){return ZoneOffset.UTC;}
  public Clock withZone(ZoneId zone){return this;}
  public Instant instant(){return armed&&calls.getAndIncrement()>0?Instant.parse("2026-11-04T15:00:01Z"):Instant.parse("2026-11-04T14:59:59Z");}
 }
 @TestConfiguration static class Config {
  @Bean @Primary BoundaryClock boundaryClock(){return new BoundaryClock();}
  @Bean RecordSnapshotReader reader(){return id->Optional.of(new Snapshot(id,777,true,"topic","url",null,new Analysis("a","b","c","d"),List.of(new Message(1,"USER","hello",OffsetDateTime.parse("2026-11-04T23:58:00+09:00")))));}
 }
}
