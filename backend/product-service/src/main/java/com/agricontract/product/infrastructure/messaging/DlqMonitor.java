package com.agricontract.product.infrastructure.messaging;

import com.agricontract.product.infrastructure.config.RabbitMQConfig;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.impl.DefaultExceptionHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;

import java.io.IOException;
import java.util.concurrent.TimeoutException;

/**
 * Warns while a product-svc DLQ is non-empty, so dead-lettered events do not sit there unnoticed. Passive counts
 * only, on an isolated connection that cannot close or block the consumer channels (same as reputation-service).
 */
@Slf4j
public class DlqMonitor {

    private final ConnectionFactory connections;
    private final int timeout;

    public DlqMonitor(ConnectionFactory source, int timeout, long intervalMillis) {
        if (timeout < 1000 || timeout > 10000 || intervalMillis < 30000 || intervalMillis > 3600000) {
            throw new IllegalArgumentException("DLQ monitor timing outside supported bounds");
        }
        this.timeout = timeout;
        // clone keeps broker/TLS credentials without changing the consumer configuration
        this.connections = source.clone();
        connections.setConnectionTimeout(timeout);
        connections.setHandshakeTimeout(timeout);
        connections.setChannelRpcTimeout(timeout);
        connections.setShutdownTimeout(timeout);
        connections.setAutomaticRecoveryEnabled(false);
        connections.setTopologyRecoveryEnabled(false);
        connections.setExceptionHandler(new DefaultExceptionHandler() {
            @Override
            public void handleUnexpectedConnectionDriverException(Connection connection, Throwable failure) {
                // the synchronous probe below reports the failure without the driver's raw exception message
            }
        });
    }

    @Scheduled(fixedDelayString = "${product.dlq-monitor.interval-ms:300000}",
            initialDelayString = "${product.dlq-monitor.interval-ms:300000}")
    public void checkDlqDepth() {
        for (String queue : RabbitMQConfig.DLQS) {
            try {
                Connection connection = connections.newConnection("product-dlq-monitor");
                try {
                    var channel = connection.createChannel();
                    if (channel == null) {
                        throw new IOException("No monitor channel");
                    }
                    int count = channel.queueDeclarePassive(queue).getMessageCount();
                    if (count > 0) {
                        log.warn("event=product_dlq_nonempty queue={} messageCount={}", queue, count);
                    }
                } finally {
                    connection.abort(timeout);
                }
            } catch (IOException | TimeoutException | RuntimeException failure) {
                // never log broker exception details, credentials or message bodies
                log.warn("event=product_dlq_probe_failed queue={}", queue);
            }
        }
    }
}
