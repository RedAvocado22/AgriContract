package com.agricontract.escrow.infrastructure.messaging;

import com.agricontract.escrow.application.exception.EscrowAccountNotFoundException;
import com.agricontract.escrow.domain.event.DomainEvent;
import com.agricontract.escrow.domain.model.EscrowAccount;
import com.agricontract.escrow.domain.model.vo.EscrowStatus;
import com.agricontract.escrow.domain.model.vo.Money;
import com.agricontract.escrow.domain.repository.EscrowAccountRepository;
import com.agricontract.escrow.infrastructure.persistence.entity.EscrowDomainEventJpaEntity;
import com.agricontract.escrow.infrastructure.persistence.repository.EscrowDomainEventJpaRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class ContractEventConsumer {

    private final EscrowAccountRepository escrowAccountRepository;
    private final EscrowDomainEventJpaRepository escrowDomainEventJpaRepository;
    private final ObjectMapper objectMapper;

    @RabbitListener(queues = "escrow-svc.contract.signed")
    public void onContractSigned(Map<String, Object> event) {
        String contractId = (String) event.get("contractId");

        if (escrowAccountRepository.existsByContractId(contractId)) {
            return;
        }

        Map<String, Object> terms = (Map<String, Object>) event.get("terms");
        Map<String, Object> agreedPriceMap = (Map<String, Object>) terms.get("agreedPrice");

        BigDecimal amount = new BigDecimal(agreedPriceMap.get("amount").toString());
        String currency = (String) agreedPriceMap.get("currency");
        Money agreedMoney = new Money(amount, currency);

        BigDecimal sellerDepositRate = new BigDecimal(terms.get("sellerDepositRate").toString());

        EscrowAccount account = EscrowAccount.lockBuyerPayment(contractId,
                (String) event.get("buyerId"), (String) event.get("sellerId"),
                (String) event.get("buyerEmail"), (String) event.get("sellerEmail"),
                sellerDepositRate, agreedMoney
        );
        escrowAccountRepository.save(account);
    }

    @RabbitListener(queues = "escrow-svc.contract.delivered")
    @Transactional
    public void onContractDelivered(Map<String, Object> event) {
        String contractId = (String) event.get("contractId");

        EscrowAccount account = escrowAccountRepository.findByContractId(contractId)
                .orElseThrow(() -> new EscrowAccountNotFoundException(contractId));

        if (account.getStatus() == EscrowStatus.RELEASED) {
            log.info("{} has been released", contractId);
            return;
        }

        account.release();
        escrowAccountRepository.save(account);

        for (DomainEvent domainEvent : account.pullDomainEvents()) {
            String payload;
            try {
                payload = objectMapper.writeValueAsString(domainEvent);
            } catch (JsonProcessingException e) {
                throw new IllegalStateException("Failed to serialize domain event " + domainEvent.getEventId(), e);
            }

            EscrowDomainEventJpaEntity entity = EscrowDomainEventJpaEntity.builder()
                    .eventId(domainEvent.getEventId().toString())
                    .eventType(domainEvent.getEventType())
                    .aggregateId(account.getEscrowId().value())
                    .payload(payload)
                    .status(EscrowDomainEventJpaEntity.Status.PENDING)
                    .build();

            escrowDomainEventJpaRepository.save(entity);
        }
    }

    @RabbitListener(queues = "escrow-svc.contract.cancelled")
    public void onContractCancelled(Map<String, Object> event) {
        // TODO: EscrowAccount.penalizeBuyer() or penalizeSeller()
    }
}
