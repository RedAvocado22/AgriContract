package com.agricontract.gateway.config;

import org.springframework.cloud.gateway.config.HttpClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class HttpClientConfig {

    /**
     * Gateway design 5.1: the gateway never re-sends a request on its own. No route carries a Retry filter, and
     * this also turns off Reactor Netty's transparent single retry on a dropped pooled connection, so a write
     * reaches the owning service at most once per client attempt.
     */
    @Bean
    public HttpClientCustomizer disableTransportRetry() {
        return httpClient -> httpClient.disableRetry(true);
    }
}
