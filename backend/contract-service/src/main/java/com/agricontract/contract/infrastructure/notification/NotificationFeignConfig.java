package com.agricontract.contract.infrastructure.notification;

import feign.Request;
import feign.RequestInterceptor;
import feign.Retryer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;

import java.util.concurrent.TimeUnit;

/**
 * Only for {@link NotificationServiceClient}; deliberately not @Configuration (see FeignConfig).
 * notification-service bounds the whole OTP call to 4500 ms, so a read timeout of at least 5000 ms means "timed out"
 * here cannot happen while it is still sending (#167). No retry: a second call must use a new challenge.
 */
public class NotificationFeignConfig {
    static final int CONNECT_TIMEOUT_MS = 1000;
    static final int READ_TIMEOUT_MS = 5000;

    @Value("${service.internal-secret}")
    private String serviceInternalSecret;

    @Bean
    public RequestInterceptor notificationInternalSecret() {
        return template -> template.header("X-Internal-Secret", serviceInternalSecret);
    }

    @Bean
    public Request.Options notificationTimeouts() {
        return new Request.Options(CONNECT_TIMEOUT_MS, TimeUnit.MILLISECONDS, READ_TIMEOUT_MS, TimeUnit.MILLISECONDS,
                true);
    }

    @Bean
    public Retryer notificationNeverRetry() {
        return Retryer.NEVER_RETRY;
    }
}
