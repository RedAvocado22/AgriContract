package com.agricontract.gateway.filter;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.cloud.gateway.support.ipresolver.RemoteAddressResolver;
import org.springframework.cloud.gateway.support.ipresolver.XForwardedRemoteAddressResolver;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.time.Clock;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Per-client-IP request bucket for a single route (gateway design 3.4 and 5.3): a fixed one-minute window,
 * answering 429 with Retry-After and never forwarding the rejected request.
 *
 * <p>Counters live in this gateway instance's memory. That is enough for the single Phase 2 gateway and keeps
 * the edge independent of Redis; running several gateway replicas would multiply the effective limit and needs
 * a shared store. Nginx still holds the coarse per-IP limit in front of this.
 *
 * <p>The client IP is the socket peer unless {@code gateway.rate-limit.trusted-proxies} is set, in which case
 * it is read from X-Forwarded-For that many hops from the right, so a client cannot pick its own bucket.
 */
@Component
public class ClientIpRateLimitGatewayFilterFactory
        extends AbstractGatewayFilterFactory<ClientIpRateLimitGatewayFilterFactory.Config> {

    static final long WINDOW_MILLIS = 60_000;
    private static final int EVICTION_THRESHOLD = 10_000;

    private final RemoteAddressResolver addressResolver;
    private final Clock clock;

    @Autowired
    public ClientIpRateLimitGatewayFilterFactory(@Value("${gateway.rate-limit.trusted-proxies:0}") int trustedProxies) {
        this(trustedProxies, Clock.systemUTC());
    }

    ClientIpRateLimitGatewayFilterFactory(int trustedProxies, Clock clock) {
        super(Config.class);
        if (trustedProxies < 0) {
            throw new IllegalArgumentException("gateway.rate-limit.trusted-proxies must not be negative");
        }
        this.addressResolver = trustedProxies == 0
                ? new RemoteAddressResolver() { }
                : XForwardedRemoteAddressResolver.maxTrustedIndex(trustedProxies);
        this.clock = clock;
    }

    @Override
    public List<String> shortcutFieldOrder() {
        return List.of("requestsPerMinute");
    }

    @Override
    public GatewayFilter apply(Config config) {
        int limit = config.getRequestsPerMinute();
        if (limit <= 0) {
            throw new IllegalArgumentException("ClientIpRateLimit requestsPerMinute must be positive");
        }
        ConcurrentMap<String, Window> windows = new ConcurrentHashMap<>();
        return (exchange, chain) -> {
            long now = clock.millis();
            if (windows.size() > EVICTION_THRESHOLD) {
                windows.values().removeIf(window -> window.expiredAt(now));
            }
            Window window = windows.compute(clientIp(exchange),
                    (ip, current) -> current == null || current.expiredAt(now)
                            ? new Window(now, 1)
                            : new Window(current.startMillis(), current.count() + 1));
            if (window.count() > limit) {
                return tooManyRequests(exchange, window, now);
            }
            return chain.filter(exchange);
        };
    }

    private String clientIp(ServerWebExchange exchange) {
        InetSocketAddress address = addressResolver.resolve(exchange);
        if (address == null) {
            return "unknown";
        }
        return address.getAddress() != null ? address.getAddress().getHostAddress() : address.getHostString();
    }

    private static Mono<Void> tooManyRequests(ServerWebExchange exchange, Window window, long now) {
        long retryAfterSeconds = Math.max(1, (window.startMillis() + WINDOW_MILLIS - now + 999) / 1000);
        exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
        exchange.getResponse().getHeaders().set("Retry-After", Long.toString(retryAfterSeconds));
        return exchange.getResponse().setComplete();
    }

    private record Window(long startMillis, int count) {

        boolean expiredAt(long now) {
            return now - startMillis >= WINDOW_MILLIS;
        }
    }

    public static class Config {

        private int requestsPerMinute;

        public int getRequestsPerMinute() {
            return requestsPerMinute;
        }

        public void setRequestsPerMinute(int requestsPerMinute) {
            this.requestsPerMinute = requestsPerMinute;
        }
    }
}
