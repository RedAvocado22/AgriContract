package com.agricontract.product.infrastructure.messaging;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class DlqMonitorTest {

    private static final String DLQ = "product-svc.contract.created.dlq";

    private final ConnectionFactory source = mock(ConnectionFactory.class);
    private final ConnectionFactory probe = mock(ConnectionFactory.class);
    private final Connection connection = mock(Connection.class);
    private final Channel channel = mock(Channel.class);
    private final Logger logger = (Logger) LoggerFactory.getLogger(DlqMonitor.class);
    private final ListAppender<ILoggingEvent> logs = new ListAppender<>();
    private DlqMonitor monitor;

    @BeforeEach
    void setUp() throws Exception {
        logs.start();
        logger.addAppender(logs);
        when(source.clone()).thenReturn(probe);
        when(probe.newConnection("product-dlq-monitor")).thenReturn(connection);
        when(connection.createChannel()).thenReturn(channel);
        when(channel.queueDeclarePassive(anyString())).thenReturn(depth(0));
        monitor = new DlqMonitor(source, 1000, 300000);
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(logs);
        logs.stop();
    }

    @Test
    void nonEmptyDlq_logsWarning() throws Exception {
        when(channel.queueDeclarePassive(DLQ)).thenReturn(depth(2));

        monitor.checkDlqDepth();

        assertThat(logs.list).singleElement().satisfies(event -> {
            assertThat(event.getLevel().toString()).isEqualTo("WARN");
            assertThat(event.getFormattedMessage())
                    .isEqualTo("event=product_dlq_nonempty queue=" + DLQ + " messageCount=2");
        });
        verify(connection).abort(1000);
    }

    @Test
    void emptyDlq_logsNothing() throws Exception {
        monitor.checkDlqDepth();

        assertThat(logs.list).isEmpty();
        verify(channel).queueDeclarePassive(DLQ);
        verifyNoMoreInteractions(channel);
    }

    @Test
    void failedProbe_logsQueueOnlyAndDoesNotThrow() throws Exception {
        when(channel.queueDeclarePassive(DLQ)).thenThrow(new IOException("password=secret payload=private"));
        when(probe.newConnection("product-dlq-monitor"))
                .thenReturn(connection)
                .thenThrow(new TimeoutException("private"));

        assertThatCode(monitor::checkDlqDepth).doesNotThrowAnyException();
        assertThatCode(monitor::checkDlqDepth).doesNotThrowAnyException();

        assertThat(logs.list).hasSize(2).allSatisfy(event -> {
            assertThat(event.getFormattedMessage()).isEqualTo("event=product_dlq_probe_failed queue=" + DLQ);
            assertThat(event.getThrowableProxy()).isNull();
        });
    }

    @Test
    void probeConnection_isBoundedAndIsolated() {
        verify(probe).setConnectionTimeout(1000);
        verify(probe).setHandshakeTimeout(1000);
        verify(probe).setChannelRpcTimeout(1000);
        verify(probe).setShutdownTimeout(1000);
        verify(probe).setAutomaticRecoveryEnabled(false);
        verify(probe).setTopologyRecoveryEnabled(false);
        verify(source).clone();
        verifyNoMoreInteractions(source);
    }

    @ParameterizedTest
    @CsvSource({"0,300000", "10001,300000", "1000,29999", "1000,3600001"})
    void outOfBoundsTiming_isRefused(int timeout, long interval) {
        assertThatThrownBy(() -> new DlqMonitor(source, timeout, interval))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static AMQP.Queue.DeclareOk depth(int count) {
        return new AMQP.Queue.DeclareOk.Builder().queue("").messageCount(count).consumerCount(0).build();
    }
}
