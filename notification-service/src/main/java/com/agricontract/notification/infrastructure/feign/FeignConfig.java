package com.agricontract.notification.infrastructure.feign;

import feign.RequestInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;

public class FeignConfig {
    @Value("${service.internal-secret}")
    private String serviceInternalSecret;

    @Bean
    public RequestInterceptor internalSecretInterceptor() {
        return template -> template.header("X-Internal-Secret", serviceInternalSecret);
    }
}
