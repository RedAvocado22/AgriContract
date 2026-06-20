package com.agricontract.escrow.infrastructure.messaging;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class ContractEventConsumer {

    @RabbitListener(queues = "escrow-svc.contract.signed")
    public void onContractSigned(Map<String, Object> event) {
        // TODO: idempotency check → EscrowAccount.lock()
    }

    @RabbitListener(queues = "escrow-svc.contract.delivered")
    public void onContractDelivered(Map<String, Object> event) {
        // TODO: EscrowAccount.release()
    }

    @RabbitListener(queues = "escrow-svc.contract.cancelled")
    public void onContractCancelled(Map<String, Object> event) {
        // TODO: EscrowAccount.penalizeBuyer() or penalizeSeller()
    }
}
