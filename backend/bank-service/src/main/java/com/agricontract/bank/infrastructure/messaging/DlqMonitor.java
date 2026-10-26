package com.agricontract.bank.infrastructure.messaging;

import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.List;

/**
 * Warns while a DLQ holds messages, so a rejected money command is noticed instead of sitting there. Passive count
 * on its own short-lived connection: it cannot block or close the consumer's channels. Same approach as
 * reputation-service.
 */
@Slf4j
public class DlqMonitor {

    private final ConnectionFactory connections;
    private final List<String> queues;
    private final int timeoutMillis;

    public DlqMonitor(ConnectionFactory source, List<String> queues, int timeoutMillis) {
        this.connections = source.clone();
        this.connections.setConnectionTimeout(timeoutMillis);
        this.connections.setHandshakeTimeout(timeoutMillis);
        this.connections.setChannelRpcTimeout(timeoutMillis);
        this.connections.setAutomaticRecoveryEnabled(false);
        this.connections.setTopologyRecoveryEnabled(false);
        this.queues = queues;
        this.timeoutMillis = timeoutMillis;
    }

    @Scheduled(fixedDelayString = "${bank.dlq-monitor.interval-ms:300000}",
            initialDelayString = "${bank.dlq-monitor.interval-ms:300000}")
    public void checkDlqDepth() {
        for (String queue : queues) {
            try {
                Connection connection = connections.newConnection("bank-dlq-monitor");
                try {
                    int count = connection.createChannel().queueDeclarePassive(queue).getMessageCount();
                    if (count > 0) {
                        log.warn("event=bank_dlq_nonempty queue={} messageCount={}", queue, count);
                    }
                } finally {
                    connection.abort(timeoutMillis);
                }
            } catch (Exception e) {
                // Never log broker details or message bodies.
                log.warn("event=bank_dlq_probe_failed queue={}", queue);
            }
        }
    }
}
