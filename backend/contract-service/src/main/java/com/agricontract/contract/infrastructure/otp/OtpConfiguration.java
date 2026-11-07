package com.agricontract.contract.infrastructure.otp;

import com.agricontract.contract.domain.model.signature.OtpPolicy;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(OtpProperties.class)
public class OtpConfiguration {

    @Bean
    OtpPolicy otpPolicy(OtpProperties properties) {
        return new OtpPolicy(properties.expirySeconds(), properties.maxAttempts(),
                properties.resendCooldownSeconds(), properties.maxSendsPerHour());
    }

    @Bean
    HmacOtpCodes otpCodes(OtpProperties properties) {
        return new HmacOtpCodes(properties.pepper());
    }
}
