package com.agricontract.escrow.infrastructure.config;

import com.agricontract.escrow.domain.model.RetrySchedule;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
@EnableConfigurationProperties(DepositLockRetryProperties.class)
public class DepositLockRetryConfig {

    @Bean
    RetrySchedule depositLockRetrySchedule(DepositLockRetryProperties properties) {
        return new RetrySchedule(properties.retryBackoff());
    }
}
