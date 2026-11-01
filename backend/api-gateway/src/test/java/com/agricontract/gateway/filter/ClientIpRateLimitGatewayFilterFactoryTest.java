package com.agricontract.gateway.filter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ClientIpRateLimitGatewayFilterFactoryTest {

    private MutableClock clock;
    private AtomicInteger forwarded;
    private GatewayFilterChain chain;

    @BeforeEach
    void setUp() {
        clock = new MutableClock();
        forwarded = new AtomicInteger();
        chain = exchange -> {
            forwarded.incrementAndGet();
            return Mono.empty();
        };
    }

    @Test
    void requestsOverTheLimit_areRejectedUntilTheWindowEnds() {
        GatewayFilter filter = filter(0, 2);

        assertThat(call(filter, "10.0.0.1", null).getResponse().getStatusCode()).isNull();
        assertThat(call(filter, "10.0.0.1", null).getResponse().getStatusCode()).isNull();

        clock.advance(20_000);
        MockServerWebExchange rejected = call(filter, "10.0.0.1", null);
        assertThat(rejected.getResponse().getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(rejected.getResponse().getHeaders().getFirst("Retry-After")).isEqualTo("40");
        assertThat(forwarded).hasValue(2);

        clock.advance(40_000);
        assertThat(call(filter, "10.0.0.1", null).getResponse().getStatusCode()).isNull();
        assertThat(forwarded).hasValue(3);
    }

    @Test
    void buckets_areSeparatePerClientIp() {
        GatewayFilter filter = filter(0, 1);

        call(filter, "10.0.0.1", null);
        assertThat(call(filter, "10.0.0.1", null).getResponse().getStatusCode())
                .isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(call(filter, "10.0.0.2", null).getResponse().getStatusCode()).isNull();
    }

    @Test
    void withoutTrustedProxy_clientSuppliedForwardedFor_cannotPickANewBucket() {
        GatewayFilter filter = filter(0, 1);

        call(filter, "10.0.0.1", "1.1.1.1");
        MockServerWebExchange spoofed = call(filter, "10.0.0.1", "2.2.2.2");

        assertThat(spoofed.getResponse().getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }

    @Test
    void withOneTrustedProxy_bucketIsTheAddressThatProxyAppended() {
        GatewayFilter filter = filter(1, 1);

        call(filter, "192.168.0.10", "6.6.6.6, 1.1.1.1");
        MockServerWebExchange sameClient = call(filter, "192.168.0.10", "7.7.7.7, 1.1.1.1");
        MockServerWebExchange otherClient = call(filter, "192.168.0.10", "2.2.2.2");

        assertThat(sameClient.getResponse().getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(otherClient.getResponse().getStatusCode()).isNull();
    }

    @Test
    void nonPositiveLimit_isRefused() {
        ClientIpRateLimitGatewayFilterFactory factory = new ClientIpRateLimitGatewayFilterFactory(0, clock);
        ClientIpRateLimitGatewayFilterFactory.Config config = new ClientIpRateLimitGatewayFilterFactory.Config();
        config.setRequestsPerMinute(0);

        assertThatThrownBy(() -> factory.apply(config)).isInstanceOf(IllegalArgumentException.class);
    }

    private GatewayFilter filter(int trustedProxies, int requestsPerMinute) {
        ClientIpRateLimitGatewayFilterFactory.Config config = new ClientIpRateLimitGatewayFilterFactory.Config();
        config.setRequestsPerMinute(requestsPerMinute);
        return new ClientIpRateLimitGatewayFilterFactory(trustedProxies, clock).apply(config);
    }

    private MockServerWebExchange call(GatewayFilter filter, String peer, String forwardedFor) {
        MockServerHttpRequest.BaseBuilder<?> request = MockServerHttpRequest.post("/api/v1/security/emergency-lock")
                .remoteAddress(new InetSocketAddress(peer, 40000));
        if (forwardedFor != null) {
            request.header("X-Forwarded-For", forwardedFor);
        }
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        filter.filter(exchange, chain).block();
        return exchange;
    }

    private static final class MutableClock extends Clock {

        private long millis = Instant.parse("2026-11-01T00:00:00Z").toEpochMilli();

        void advance(long delta) {
            millis += delta;
        }

        @Override
        public long millis() {
            return millis;
        }

        @Override
        public Instant instant() {
            return Instant.ofEpochMilli(millis);
        }

        @Override
        public java.time.ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }
    }
}
