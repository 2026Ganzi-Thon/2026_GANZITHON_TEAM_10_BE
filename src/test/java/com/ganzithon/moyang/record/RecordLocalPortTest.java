package com.ganzithon.moyang.record;
import com.ganzithon.moyang.common.port.Ports.RecordPort;
import com.ganzithon.moyang.record.adapter.RecordPortAdapter;
import com.ganzithon.moyang.record.domain.ReflectionRecord.Status;
import com.ganzithon.moyang.record.repository.RecordRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import static org.assertj.core.api.Assertions.assertThat;
@SpringBootTest(properties="spring.datasource.url=jdbc:h2:mem:record-local;MODE=MySQL;DB_CLOSE_DELAY=-1")
@ActiveProfiles({"local","test"})
class RecordLocalPortTest {
 @Autowired RecordPort port; @Autowired RecordRepository records;
 @Test void localProfileUsesRealRecordIdentityAndPersistedReadySignal(){
  assertThat(port).isInstanceOf(RecordPortAdapter.class);
  long id=port.create(777);
  assertThat(records.findById(id).orElseThrow().getUserId()).isEqualTo(777);
  port.cardReady(id);
  assertThat(records.findById(id).orElseThrow().getStatus()).isEqualTo(Status.CARD_READY);
 }
}
