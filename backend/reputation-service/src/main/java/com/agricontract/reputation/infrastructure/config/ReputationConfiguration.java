package com.agricontract.reputation.infrastructure.config;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
@Configuration
public class ReputationConfiguration {
    @Bean public Clock reputationClock() { return Clock.systemUTC(); }
}
