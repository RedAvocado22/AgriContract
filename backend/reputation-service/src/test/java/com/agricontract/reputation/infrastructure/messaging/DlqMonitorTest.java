package com.agricontract.reputation.infrastructure.messaging;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.agricontract.reputation.infrastructure.config.RabbitConfiguration;
import com.rabbitmq.client.*;
import java.io.IOException;
import java.net.ServerSocket;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.slf4j.LoggerFactory;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class DlqMonitorTest {
    final ConnectionFactory source = mock(ConnectionFactory.class), probe = mock(ConnectionFactory.class);
    final Connection connection = mock(Connection.class);
    final Channel channel = mock(Channel.class);
    final Logger logger = (Logger)LoggerFactory.getLogger(DlqMonitor.class);
    final ListAppender<ILoggingEvent> logs = new ListAppender<>();
    DlqMonitor monitor;
    static AMQP.Queue.DeclareOk depth(int count) {
        return new AMQP.Queue.DeclareOk.Builder().queue("").messageCount(count).consumerCount(0).build();
    }
    @BeforeEach void setup() throws Exception {
        logs.start(); logger.addAppender(logs);
        when(source.clone()).thenReturn(probe);
        when(probe.newConnection("reputation-dlq-monitor")).thenReturn(connection);
        when(connection.createChannel()).thenReturn(channel);
        when(channel.queueDeclarePassive(anyString())).thenReturn(depth(0));
        monitor = new DlqMonitor(source, 1000, 300000);
    }
    @AfterEach void cleanup() { logger.detachAppender(logs); logs.stop(); }
    @Test void nonemptyQueueEmitsSafeStructuredWarning() throws Exception {
        when(channel.queueDeclarePassive(RabbitConfiguration.DLQS.getFirst()))
                .thenReturn(depth(7));
        monitor.checkDlqDepth();
        assertThat(logs.list).hasSize(1);
        assertThat(logs.list.getFirst().getFormattedMessage()).isEqualTo(
                "event=reputation_dlq_nonempty queue=reputation-svc.contract.settled.dlq messageCount=7");
        assertThat(logs.list.getFirst().getLevel().toString()).isEqualTo("WARN");
        assertThat(logs.list.getFirst().getThrowableProxy()).isNull();
    }
    @Test void emptyQueuesEmitNoWarningsAndOnlyOwnedQueuesAreInspected() throws Exception {
        monitor.checkDlqDepth();
        assertThat(logs.list).isEmpty();
        for (String queue : RabbitConfiguration.DLQS) verify(channel).queueDeclarePassive(queue);
        verifyNoMoreInteractions(channel);
        verify(connection, times(2)).abort(1000);
    }
    @Test void failedProbeIsSafeAndDoesNotStopOtherQueuesOrFutureChecks() throws Exception {
        when(channel.queueDeclarePassive(RabbitConfiguration.DLQS.getFirst()))
                .thenThrow(new IOException("password=secret jdbc:private payload=private-person"))
                .thenReturn(depth(0));
        monitor.checkDlqDepth();
        monitor.checkDlqDepth();
        verify(channel, times(2)).queueDeclarePassive(RabbitConfiguration.DLQS.getLast());
        assertThat(logs.list).hasSize(1);
        assertThat(logs.list.getFirst().getFormattedMessage()).isEqualTo(
                "event=reputation_dlq_probe_failed queue=reputation-svc.contract.settled.dlq");
        assertThat(logs.list.getFirst().getThrowableProxy()).isNull();
        verify(connection, times(4)).abort(1000);
    }
    @Test void connectionTimeoutAndRuntimeFailuresAreContained() throws Exception {
        when(probe.newConnection("reputation-dlq-monitor"))
                .thenThrow(new TimeoutException("private")).thenThrow(new IllegalStateException("private"));
        assertThatCode(monitor::checkDlqDepth).doesNotThrowAnyException();
        assertThat(logs.list).hasSize(2).allSatisfy(event -> {
            assertThat(event.getFormattedMessage()).doesNotContain("private");
            assertThat(event.getThrowableProxy()).isNull();
        });
    }
    @Test void monitorUsesBoundedIsolatedConnectionConfiguration() {
        verify(probe).setConnectionTimeout(1000);
        verify(probe).setHandshakeTimeout(1000);
        verify(probe).setChannelRpcTimeout(1000);
        verify(probe).setShutdownTimeout(1000);
        verify(probe).setAutomaticRecoveryEnabled(false);
        verify(probe).setTopologyRecoveryEnabled(false);
        verify(source).clone();
        verifyNoMoreInteractions(source);
    }
    @Test void asynchronousMonitorDriverDoesNotLogRawConnectionExceptions() {
        var handler = org.mockito.ArgumentCaptor.forClass(ExceptionHandler.class);
        verify(probe).setExceptionHandler(handler.capture());
        handler.getValue().handleUnexpectedConnectionDriverException(connection,
                new IOException("private-user password=secret payload=private"));
        assertThat(logs.list).isEmpty();
    }
    @ParameterizedTest @CsvSource({"0,300000", "10001,300000", "1000,0", "1000,29999", "1000,3600001"})
    void refusesUnboundedTimeoutsAndTightPolling(int timeout, long interval) {
        assertThatThrownBy(() -> new DlqMonitor(source, timeout, interval)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void stalledBrokerHandshakeIsTimeBounded() throws Exception {
        try (var server = new ServerSocket(0)) {
            var finish = new CountDownLatch(1);
            Thread acceptor = Thread.startVirtualThread(() -> {
                try {
                    while (!server.isClosed()) {
                        var socket = server.accept();
                        Thread.startVirtualThread(() -> {
                            try (socket) { finish.await(10, TimeUnit.SECONDS); } catch (Exception ignored) {}
                        });
                    }
                } catch (IOException expectedOnClose) {}
            });
            var real = new ConnectionFactory();
            real.setHost("127.0.0.1"); real.setPort(server.getLocalPort());
            try {
                Assertions.assertTimeoutPreemptively(Duration.ofSeconds(8),
                        () -> new DlqMonitor(real, 1000, 300000).checkDlqDepth());
                assertThat(logs.list).hasSize(2);
            } finally { finish.countDown(); server.close(); acceptor.join(2000); }
        }
    }
}
