package com.ganzithon.moyang.sheep;
import com.ganzithon.moyang.common.port.Ports.WoolPort;
import com.ganzithon.moyang.sheep.adapter.WoolPortAdapter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import static org.assertj.core.api.Assertions.assertThat;
@SpringBootTest(properties="spring.datasource.url=jdbc:h2:mem:sheep-local;MODE=MySQL;DB_CLOSE_DELAY=-1")
@ActiveProfiles({"local","test"})
class SheepLocalPortTest {
 @Autowired WoolPort wool;
 @Test void localProfileUsesRealWalletRatherThanFakeBalance(){
  assertThat(wool).isInstanceOf(WoolPortAdapter.class);
  assertThat(wool.balance(55555)).isZero();
 }
}
