package com.agricontract.notification.infrastructure.messaging;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Properties;

@Slf4j
@Component
@RequiredArgsConstructor
public class DlqMonitor {
    private final AmqpAdmin amqpAdmin;
    private final Declarables declarables;

    private List<String> dlqNames() {
        return declarables.getDeclarablesByType(Queue.class).stream()
                .map(Queue::getName)
                .filter(name -> name.endsWith(".dlq"))
                .toList();
    }

    @Scheduled(fixedDelay = 300000)
    public void checkDlqDepth() {
        for (String dlq : dlqNames()) {
            Properties props = amqpAdmin.getQueueProperties(dlq);
            if (props == null) continue;
            Integer count = (Integer) props.get(RabbitAdmin.QUEUE_MESSAGE_COUNT);
            if (count != null && count > 0) {
                log.warn("DLQ {} has {} message(s) — need manual fix", dlq, count);
            }
        }
    }
}
