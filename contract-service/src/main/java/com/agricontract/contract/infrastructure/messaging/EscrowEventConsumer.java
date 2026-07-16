package com.agricontract.contract.infrastructure.messaging;

import com.agricontract.contract.application.dto.ActivateContractCommand;
import com.agricontract.contract.application.dto.SettleContractCommand;
import com.agricontract.contract.application.dto.ResolveDisputeCommand;
import com.agricontract.contract.application.exception.InvalidEventPayloadException;
import com.agricontract.contract.application.usecase.ActivateContractUseCase;
import com.agricontract.contract.application.usecase.SettleContractUseCase;
import com.agricontract.contract.application.usecase.ResolveDisputeUseCase;
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
    private final ResolveDisputeUseCase resolveDisputeUseCase;

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

    @RabbitListener(queues = "contract-svc.escrow.arbitrated")
    public void onEscrowArbitrated(Map<String, Object> event) {
        log.debug("Received escrow.arbitrated event: {}", event);
        resolveDisputeUseCase.execute(parseResolveDisputeEvent(event));
    }

    private ResolveDisputeCommand parseResolveDisputeEvent(Map<String, Object> event) {
        try {
            String contractId = (String) event.get("contractId");
            if (contractId == null || contractId.isBlank()) {
                throw new IllegalArgumentException("contractId is required");
            }
            return new ResolveDisputeCommand(contractId);
        } catch (RuntimeException exception) {
            throw new InvalidEventPayloadException(
                    "Failed to parse escrow.arbitrated event: " + event, exception);
        }
    }
}
