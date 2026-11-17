package com.ganzithon.moyang.sheep.config;
import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.*;
@Configuration
public class SheepConfiguration {
    @Bean @ConditionalOnMissingBean(Clock.class) public Clock sheepClock(){return Clock.systemUTC();}
}
