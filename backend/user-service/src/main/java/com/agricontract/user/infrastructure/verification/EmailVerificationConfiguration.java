package com.agricontract.user.infrastructure.verification;

import com.agricontract.user.domain.model.EmailVerificationPolicy;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(EmailVerificationProperties.class)
public class EmailVerificationConfiguration {

    @Bean
    EmailVerificationPolicy emailVerificationPolicy(EmailVerificationProperties properties) {
        return new EmailVerificationPolicy(properties.expirySeconds(), properties.maxAttempts(),
                properties.resendCooldownSeconds());
    }

    @Bean
    HmacVerificationCodes verificationCodes(EmailVerificationProperties properties) {
        return new HmacVerificationCodes(properties.pepper());
    }
}
