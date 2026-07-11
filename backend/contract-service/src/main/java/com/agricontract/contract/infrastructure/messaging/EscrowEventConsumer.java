package com.agricontract.contract.infrastructure.messaging;

import com.agricontract.contract.application.dto.ActivateContractCommand;
import com.agricontract.contract.application.dto.SettleContractCommand;
import com.agricontract.contract.application.exception.InvalidEventPayloadException;
import com.agricontract.contract.application.usecase.ActivateContractUseCase;
import com.agricontract.contract.application.usecase.SettleContractUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class EscrowEventConsumer {

    private final ActivateContractUseCase activateContractUseCase;
    private final SettleContractUseCase settleContractUseCase;

    @RabbitListener(queues = "contract-svc.escrow.locked")
    public void onEscrowLocked(Map<String, Object> event) {
        log.debug("Received escrow.locked event: {}", event);
        activateContractUseCase.execute(parseActivateEvent(event));
    }

    private ActivateContractCommand parseActivateEvent(Map<String, Object> event) {
        try {
            String contractId = (String) event.get("contractId");
            return new ActivateContractCommand(contractId);
        } catch (RuntimeException e) {
            throw new InvalidEventPayloadException("Failed to parse escrow.locked event: " + event, e);
        }
    }

    @RabbitListener(queues = "contract-svc.escrow.released")
    public void onEscrowReleased(Map<String, Object> event) {
        log.debug("Received escrow.released event: {}", event);
        settleContractUseCase.execute(parseSettleEvent(event));
    }

    private SettleContractCommand parseSettleEvent(Map<String, Object> event) {
        try {
            String contractId = (String) event.get("contractId");
            return new SettleContractCommand(contractId);
        } catch (RuntimeException e) {
            throw new InvalidEventPayloadException("Failed to parse escrow.released event: " + event, e);
        }
    }
}
