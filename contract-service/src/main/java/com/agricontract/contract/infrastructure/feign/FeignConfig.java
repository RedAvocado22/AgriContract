package com.agricontract.contract.infrastructure.feign;

import feign.RequestInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;

// Deliberately NOT @Configuration: this class is only referenced via
// @FeignClient(configuration = FeignConfig.class). If it were also picked up
// by component scan, Spring Cloud OpenFeign would register its beans in the
// global context and apply them to every Feign client, not just the ones
// that declare it explicitly.
public class FeignConfig {

    @Value("${service.internal-secret}")
    private String serviceInternalSecret;

    @Bean
    public RequestInterceptor internalSecretInterceptor() {
        return template -> template.header("X-Internal-Secret", serviceInternalSecret);
    }
}
