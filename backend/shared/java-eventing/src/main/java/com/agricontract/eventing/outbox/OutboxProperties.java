package com.agricontract.eventing.outbox;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * agricontract.eventing.outbox.*. The poller is off unless enabled=true, so a service can depend on the module
 * (and write to event_outbox) before it switches publishing over from its legacy poller.
 * pollInterval and staleCheckInterval are read by @Scheduled placeholders, which only understand milliseconds or
 * ISO-8601 (PT1S), not Boot's "1s" shorthand; the auto-configuration rejects other formats at startup.
 */
@ConfigurationProperties("agricontract.eventing.outbox")
public record OutboxProperties(
        boolean enabled,
        String exchange,
        @DefaultValue("50") int batchSize,
        @DefaultValue("PT1S") Duration pollInterval,
        @DefaultValue("5s") Duration confirmTimeout,
        @DefaultValue("PT5M") Duration staleThreshold,
        @DefaultValue("PT1M") Duration staleCheckInterval) {
}
