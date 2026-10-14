package com.agricontract.reputation.infrastructure.messaging;

import com.agricontract.reputation.infrastructure.config.RabbitConfiguration;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.impl.DefaultExceptionHandler;
import java.io.IOException;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

/** Passive counts only; isolated connections cannot close or block consumer channels. */
public class DlqMonitor {
    private static final Logger LOG = LoggerFactory.getLogger(DlqMonitor.class);
    private final ConnectionFactory connections;
    private final int timeout;
    public DlqMonitor(ConnectionFactory source, int timeout, long intervalMillis) {
        if (timeout < 1000 || timeout > 10000 || intervalMillis < 30000 || intervalMillis > 3600000)
            throw new IllegalArgumentException("DLQ monitor timing outside supported bounds");
        this.timeout = timeout;
        connections = source.clone(); // Retain broker/TLS credentials without changing consumer configuration.
        connections.setConnectionTimeout(timeout);
        connections.setHandshakeTimeout(timeout);
        connections.setChannelRpcTimeout(timeout);
        connections.setShutdownTimeout(timeout);
        connections.setAutomaticRecoveryEnabled(false);
        connections.setTopologyRecoveryEnabled(false);
        connections.setExceptionHandler(new DefaultExceptionHandler() {
            @Override public void handleUnexpectedConnectionDriverException(Connection connection, Throwable failure) {
                // The synchronous probe below reports failure without the driver's raw exception message.
            }
        });
    }
    @Scheduled(fixedDelayString = "${reputation.dlq-monitor.interval-ms:300000}",
            initialDelayString = "${reputation.dlq-monitor.interval-ms:300000}")
    public void checkDlqDepth() {
        for (String queue : RabbitConfiguration.DLQS) {
            try {
                Connection connection = connections.newConnection("reputation-dlq-monitor");
                try {
                    var channel = connection.createChannel();
                    if (channel == null) throw new IOException("No monitor channel");
                    int count = channel.queueDeclarePassive(queue).getMessageCount();
                    if (count > 0) LOG.warn("event=reputation_dlq_nonempty queue={} messageCount={}", queue, count);
                } finally {
                    connection.abort(timeout); // Bounded cleanup, including channel closure.
                }
            } catch (IOException | TimeoutException | RuntimeException failure) {
                // Never log broker exception details, credentials or message bodies.
                LOG.warn("event=reputation_dlq_probe_failed queue={}", queue);
            }
        }
    }
}
