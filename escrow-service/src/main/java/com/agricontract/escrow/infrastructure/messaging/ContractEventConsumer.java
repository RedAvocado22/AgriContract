package com.agricontract.escrow.infrastructure.messaging;

import com.agricontract.escrow.application.dto.LockBuyerPaymentCommand;
import com.agricontract.escrow.application.dto.PenalizeEscrowCommand;
import com.agricontract.escrow.application.dto.ReleaseEscrowCommand;
import com.agricontract.escrow.application.usecase.LockBuyerPaymentUseCase;
import com.agricontract.escrow.application.usecase.PenalizeEscrowUseCase;
import com.agricontract.escrow.application.usecase.ReleaseEscrowUseCase;
import com.agricontract.escrow.domain.model.vo.Money;
import com.agricontract.escrow.domain.model.vo.Party;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class ContractEventConsumer {

    private final LockBuyerPaymentUseCase lockBuyerPaymentUseCase;
    private final ReleaseEscrowUseCase releaseEscrowUseCase;
    private final PenalizeEscrowUseCase penalizeEscrowUseCase;

    @RabbitListener(queues = "escrow-svc.contract.signed")
    public void onContractSigned(Map<String, Object> event) {
        log.info("Received contract.signed for contract {}", event.get("contractId"));
        lockBuyerPaymentUseCase.execute(parseSignedEvent(event));
    }

    private LockBuyerPaymentCommand parseSignedEvent(Map<String, Object> event) {
        Map<String, Object> terms = (Map<String, Object>) event.get("terms");
        Map<String, Object> agreedPriceMap = (Map<String, Object>) terms.get("agreedPrice");

        BigDecimal amount = new BigDecimal(agreedPriceMap.get("amount").toString());
        String currency = (String) agreedPriceMap.get("currency");
        Money agreedMoney = new Money(amount, currency);

        BigDecimal sellerDepositRate = new BigDecimal(terms.get("sellerDepositRate").toString());

        return new LockBuyerPaymentCommand((String) event.get("contractId"),
                (String) event.get("buyerId"), (String) event.get("sellerId"),
                (String) event.get("buyerEmail"), (String) event.get("sellerEmail"),
                sellerDepositRate, agreedMoney
        );
    }

    @RabbitListener(queues = "escrow-svc.contract.delivered")
    public void onContractDelivered(Map<String, Object> event) {
        log.info("Received contract.delivered for contract {}", event.get("contractId"));
        releaseEscrowUseCase.execute(parseReleaseEvent(event));
    }

    private ReleaseEscrowCommand parseReleaseEvent(Map<String, Object> event) {
        return new ReleaseEscrowCommand((String) event.get("contractId"));
    }

    @RabbitListener(queues = "escrow-svc.contract.cancelled")
    public void onContractCancelled(Map<String, Object> event) {
        log.info("Received contract.cancelled for contract {}", event.get("contractId"));
        penalizeEscrowUseCase.execute(parseCancelledEvent(event));
    }

    private PenalizeEscrowCommand parseCancelledEvent(Map<String, Object> event) {
        Party cancelledBy = Party.valueOf((String) event.get("cancelledBy"));
        Object rate = event.get("buyerPenaltyRate");
        BigDecimal buyerPenaltyRate = rate != null ? new BigDecimal(rate.toString()) : null;

        return new PenalizeEscrowCommand((String) event.get("contractId"), cancelledBy, buyerPenaltyRate);
    }
}
