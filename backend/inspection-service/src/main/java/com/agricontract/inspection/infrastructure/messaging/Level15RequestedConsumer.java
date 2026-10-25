package com.agricontract.inspection.infrastructure.messaging;

import com.agricontract.eventing.*;
import com.agricontract.inspection.application.usecase.ManageLevel15Assignments;
import com.agricontract.inspection.domain.model.AssignmentRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class Level15RequestedConsumer {
    private final EventEnvelopeCodec codec;
    private final ManageLevel15Assignments assignments;

    @RabbitListener(queues = "inspection-svc.inspection.level1_5_requested")
    public void accept(Message message) {
        EventEnvelope event;
        AssignmentRequest request;
        try {
            event = codec.parse(message.getBody());
            if (!"inspection.level1_5_requested".equals(event.eventType())
                    || !"contract-service".equals(event.producer()) || event.eventVersion() != 1)
                throw new IllegalArgumentException("Unexpected assignment event");
            request = AssignmentRequest.from(event.payload());
        } catch (InvalidEnvelopeException | IllegalArgumentException | java.time.DateTimeException ex) {
            throw new AmqpRejectAndDontRequeueException("Invalid Level 1.5 request; inspect the restricted DLQ");
        }
        // Dependency/database failures remain retryable and roll back the processed-event claim.
        assignments.requested(event, request);
    }
}
