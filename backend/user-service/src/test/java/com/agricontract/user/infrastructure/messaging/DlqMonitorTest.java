package com.agricontract.user.infrastructure.messaging;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.agricontract.user.infrastructure.config.RabbitConfiguration;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class DlqMonitorTest {

    private static final String DLQ = RabbitConfiguration.REPUTATION_LOCK_DLQ;
    private static final int TIMEOUT = 1000;

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
        when(probe.newConnection("user-dlq-monitor")).thenReturn(connection);
        when(connection.createChannel()).thenReturn(channel);
        when(channel.queueDeclarePassive(anyString())).thenReturn(depth(0));
        monitor = new DlqMonitor(source, List.of(DLQ), TIMEOUT);
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(logs);
        logs.stop();
    }

    @Test
    void nonEmptyDlq_logsWarnWithQueueAndCount() throws Exception {
        when(channel.queueDeclarePassive(DLQ)).thenReturn(depth(7));

        monitor.checkDlqDepth();

        assertThat(logs.list).hasSize(1);
        ILoggingEvent event = logs.list.get(0);
        assertThat(event.getLevel()).isEqualTo(Level.WARN);
        assertThat(event.getFormattedMessage())
                .isEqualTo("event=user_dlq_nonempty queue=user-svc.reputation.dlq messageCount=7");
        verify(connection).abort(TIMEOUT);
    }

    @Test
    void emptyDlq_logsNothingAndOnlyCountsPassively() throws Exception {
        monitor.checkDlqDepth();

        assertThat(logs.list).isEmpty();
        verify(channel).queueDeclarePassive(DLQ);
        verifyNoMoreInteractions(channel);
        verify(connection).abort(TIMEOUT);
    }

    @Test
    void failedProbe_logsNoBrokerDetailAndStillClosesTheConnection() throws Exception {
        when(channel.queueDeclarePassive(DLQ)).thenThrow(new IOException("password=secret"));

        monitor.checkDlqDepth();

        assertThat(logs.list).hasSize(1);
        assertThat(logs.list.get(0).getFormattedMessage())
                .isEqualTo("event=user_dlq_probe_failed queue=user-svc.reputation.dlq");
        assertThat(logs.list.get(0).getThrowableProxy()).isNull();
        verify(connection).abort(TIMEOUT);
    }

    @Test
    void unreachableBroker_doesNotEscapeTheScheduledRun() throws Exception {
        when(probe.newConnection("user-dlq-monitor")).thenThrow(new TimeoutException("private"));

        assertThatCode(monitor::checkDlqDepth).doesNotThrowAnyException();

        assertThat(logs.list).hasSize(1);
        assertThat(logs.list.get(0).getFormattedMessage()).doesNotContain("private");
    }

    @Test
    void usesItsOwnBoundedConnectionSettings() {
        verify(probe).setConnectionTimeout(TIMEOUT);
        verify(probe).setHandshakeTimeout(TIMEOUT);
        verify(probe).setChannelRpcTimeout(TIMEOUT);
        verify(probe).setAutomaticRecoveryEnabled(false);
        verify(probe).setTopologyRecoveryEnabled(false);
        verify(source).clone();
        verifyNoMoreInteractions(source);
    }

    private static AMQP.Queue.DeclareOk depth(int count) {
        return new AMQP.Queue.DeclareOk.Builder().queue("").messageCount(count).consumerCount(0).build();
    }
}
