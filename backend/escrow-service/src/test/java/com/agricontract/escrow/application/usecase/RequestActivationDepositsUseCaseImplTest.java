package com.agricontract.escrow.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.agricontract.escrow.application.dto.ContractSignedCommand;
import com.agricontract.escrow.application.port.LockCommandPublisher;
import com.agricontract.escrow.domain.exception.InvalidDepositLegException;
import com.agricontract.escrow.domain.model.DepositLeg;
import com.agricontract.escrow.domain.model.DepositLegParty;
import com.agricontract.escrow.domain.model.DepositLegStatus;
import com.agricontract.escrow.domain.model.Money;
import com.agricontract.escrow.domain.repository.DepositLegRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RequestActivationDepositsUseCaseImplTest {

    private static final Instant NOW = Instant.parse("2026-10-28T03:00:00Z");
    private static final String EVENT_ID = UUID.randomUUID().toString();
    private static final String CORRELATION_ID = UUID.randomUUID().toString();
    private static final String CONTRACT_ID = UUID.randomUUID().toString();
    private static final String BUYER_ID = UUID.randomUUID().toString();
    private static final String SELLER_ID = UUID.randomUUID().toString();

    private final List<DepositLeg> inserted = new ArrayList<>();
    private final List<String> published = new ArrayList<>();  // "legId|correlationId|causationId", in call order
    private RequestActivationDepositsUseCaseImpl useCase;

    @BeforeEach
    void setUp() {
        DepositLegRepository legs = new DepositLegRepository() {
            @Override
            public void insert(DepositLeg leg) {
                inserted.add(leg);
            }

            @Override
            public List<DepositLeg> findByContractId(String contractId) {
                return inserted.stream().filter(l -> l.getContractId().equals(contractId)).toList();
            }
        };
        LockCommandPublisher publisher = (leg, correlationId, causationId) -> {
            // A command must never be published for a leg that was not stored first.
            assertThat(inserted).contains(leg);
            published.add(leg.getLegId() + "|" + correlationId + "|" + causationId);
        };
        useCase = new RequestActivationDepositsUseCaseImpl(legs, publisher, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void buyerAndSellerDepositsGiveTwoLegsAndTwoCommands() {
        useCase.execute(command("1500000.00", "750000.00"));

        assertThat(inserted).extracting(DepositLeg::getParty)
                .containsExactly(DepositLegParty.BUYER, DepositLegParty.SELLER);
        DepositLeg buyer = inserted.get(0);
        DepositLeg seller = inserted.get(1);
        assertThat(buyer.getUserId()).isEqualTo(BUYER_ID);
        assertThat(buyer.getAmount()).isEqualTo(new Money(new BigDecimal("1500000.00")));
        assertThat(seller.getUserId()).isEqualTo(SELLER_ID);
        assertThat(seller.getAmount()).isEqualTo(new Money(new BigDecimal("750000.00")));
        assertThat(inserted).allSatisfy(leg -> {
            assertThat(leg.getContractId()).isEqualTo(CONTRACT_ID);
            assertThat(leg.getStatus()).isEqualTo(DepositLegStatus.REQUESTED);
            assertThat(leg.getCreatedAt()).isEqualTo(NOW);
        });
        assertThat(published).containsExactly(
                buyer.getLegId() + "|" + CORRELATION_ID + "|" + EVENT_ID,
                seller.getLegId() + "|" + CORRELATION_ID + "|" + EVENT_ID);
    }

    @Test
    void noSellerDepositGivesOnlyTheBuyerLeg() {
        useCase.execute(command("1500000.00", "0.00"));

        assertThat(inserted).extracting(DepositLeg::getParty).containsExactly(DepositLegParty.BUYER);
        assertThat(published).hasSize(1);
    }

    @Test
    void anInvalidBuyerAmountFailsBeforeAnythingIsStoredOrSent() {
        assertThatThrownBy(() -> useCase.execute(command("0.00", "750000.00")))
                .isInstanceOf(InvalidDepositLegException.class);
        assertThat(inserted).isEmpty();
        assertThat(published).isEmpty();
    }

    @Test
    void aNegativeSellerDepositIsRejectedNotTreatedAsNoDeposit() {
        assertThatThrownBy(() -> useCase.execute(command("1500000.00", "-500.00")))
                .isInstanceOf(InvalidDepositLegException.class).hasMessageContaining("sellerDepositAmount");
        assertThat(inserted).isEmpty();
        assertThat(published).isEmpty();
    }

    @Test
    void anInvalidSellerAmountFailsBeforeTheBuyerLegIsStored() {
        assertThatThrownBy(() -> useCase.execute(command("1500000.00", "10.005")))
                .isInstanceOf(InvalidDepositLegException.class);
        assertThat(inserted).isEmpty();
        assertThat(published).isEmpty();
    }

    private static ContractSignedCommand command(String buyerDeposit, String sellerDeposit) {
        return new ContractSignedCommand(EVENT_ID, CORRELATION_ID, CONTRACT_ID, BUYER_ID, SELLER_ID,
                new BigDecimal(buyerDeposit), new BigDecimal(sellerDeposit));
    }
}
