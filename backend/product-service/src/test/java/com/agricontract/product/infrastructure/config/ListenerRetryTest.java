package com.agricontract.product.infrastructure.config;

import com.agricontract.eventing.EventEnvelopeCodec;
import com.agricontract.product.application.usecase.ConfirmContractCreatedUseCase;
import com.agricontract.product.infrastructure.messaging.ContractCreatedConsumer;
import com.rabbitmq.client.Channel;
import org.aopalliance.intercept.MethodInterceptor;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.support.ListenerExecutionFailedException;
import org.springframework.aop.ProxyMethodInvocation;
import org.springframework.dao.DataAccessResourceFailureException;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * The listener advice chain from RabbitMQConfig, driven the way the container drives it: the listener's exception
 * arrives wrapped in ListenerExecutionFailedException. A malformed contract.created is attempted once; a processing
 * failure is attempted 3 times; both end rejected (no requeue), so the broker dead-letters them.
 */
class ListenerRetryTest {

    private final ConfirmContractCreatedUseCase useCase = mock(ConfirmContractCreatedUseCase.class);
    private final ContractCreatedConsumer consumer = new ContractCreatedConsumer(new EventEnvelopeCodec(), useCase);
    private final MethodInterceptor retry = RabbitMQConfig.listenerRetry(1);
    private final AtomicInteger attempts = new AtomicInteger();

    @Test
    void malformedDelivery_isAttemptedOnce() {
        Message malformed = message("{\"eventId\":");

        assertThatThrownBy(() -> retry.invoke(delivery(malformed)))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);

        assertThat(attempts).hasValue(1);
        verifyNoInteractions(useCase);
    }

    @Test
    void processingFailure_isAttemptedThreeTimesThenRejected() {
        doThrow(new DataAccessResourceFailureException("test-only outage")).when(useCase).execute(any());

        assertThatThrownBy(() -> retry.invoke(delivery(message(validEvent()))))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);

        assertThat(attempts).hasValue(3);
        verify(useCase, times(3)).execute(any());
    }

    private ProxyMethodInvocation delivery(Message message) throws Throwable {
        ProxyMethodInvocation invocation = mock(ProxyMethodInvocation.class);
        when(invocation.getMethod()).thenReturn(ContractCreatedConsumer.class.getMethod("onContractCreated", Message.class));
        when(invocation.getArguments()).thenReturn(new Object[]{mock(Channel.class), message});
        when(invocation.invocableClone()).thenReturn(invocation);
        when(invocation.proceed()).thenAnswer(call -> {
            attempts.incrementAndGet();
            try {
                consumer.onContractCreated(message);
                return null;
            } catch (RuntimeException e) {
                throw new ListenerExecutionFailedException("Listener threw exception", e, message);
            }
        });
        return invocation;
    }

    private static String validEvent() {
        return """
                {"eventId":"5b0d7f8e-2a5c-4e7b-9a43-0c1f6e9d2b11","eventType":"contract.created","eventVersion":1,
                 "occurredAt":"2026-10-27T03:00:00Z","producer":"contract-service",
                 "aggregateId":"8f14e45f-ceea-467a-9575-6b4a8a0f3c21",
                 "payload":{"contractId":"8f14e45f-ceea-467a-9575-6b4a8a0f3c21",
                            "listingId":"c4ca4238-a0b9-4382-8dcc-509a6f75849b","reservedQuantity":40,
                            "createdAt":"2026-10-27T03:00:00Z"}}
                """;
    }

    private static Message message(String body) {
        return new Message(body.getBytes(StandardCharsets.UTF_8), new MessageProperties());
    }
}
