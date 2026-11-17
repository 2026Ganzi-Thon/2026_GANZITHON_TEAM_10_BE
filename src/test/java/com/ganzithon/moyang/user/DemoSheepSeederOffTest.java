package com.ganzithon.moyang.user;

import static org.assertj.core.api.Assertions.assertThat;

import com.ganzithon.moyang.sheep.service.SheepService;
import com.ganzithon.moyang.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/** DEMO_SHEEP_BALANCE가 없으면(기본) 계정은 만들어져도 양털은 채우지 않는다. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:demo-sheep-off;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "moyang.upload-dir=build/test-uploads",
        "moyang.demo.password=sheep-seed-pw-2"
})
class DemoSheepSeederOffTest {

    @Autowired UserRepository users;
    @Autowired SheepService sheep;

    @Test
    void doesNothingWhenDisabled() {
        long id = users.findByLoginId("demo1").orElseThrow().getId();
        assertThat(sheep.balance(id).balance()).isZero();
    }
}
