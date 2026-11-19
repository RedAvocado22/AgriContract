package com.agricontract.user.infrastructure.keycloak;

import com.agricontract.user.domain.model.UnverifiedAccountPolicy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class UnverifiedAccountConfiguration {

    // 24 h in production (D59); the demo stack sets UNVERIFIED_ACCOUNT_MAX_AGE=PT10M.
    @Bean
    UnverifiedAccountPolicy unverifiedAccountPolicy(@Value("${user.unverified-account.max-age}") Duration maxAge) {
        return new UnverifiedAccountPolicy(maxAge);
    }
}
