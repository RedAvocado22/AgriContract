package com.agricontract.escrow.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.agricontract.escrow.domain.exception.InvalidDepositLegException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DepositLegTest {

    private static final String CONTRACT_ID = UUID.randomUUID().toString();
    private static final String BUYER_ID = UUID.randomUUID().toString();
    private static final Money AMOUNT = new Money(new BigDecimal("1500000.00"));
    private static final Instant NOW = Instant.parse("2026-10-28T03:00:00.123456Z");

    @Test
    void requestCreatesARequestedLegStampedWithTheGivenTime() {
        DepositLeg leg = DepositLeg.request(CONTRACT_ID, DepositLegParty.BUYER, BUYER_ID, AMOUNT, NOW);

        assertThat(leg.getContractId()).isEqualTo(CONTRACT_ID);
        assertThat(leg.getParty()).isEqualTo(DepositLegParty.BUYER);
        assertThat(leg.getUserId()).isEqualTo(BUYER_ID);
        assertThat(leg.getAmount()).isEqualTo(AMOUNT);
        assertThat(leg.getStatus()).isEqualTo(DepositLegStatus.REQUESTED);
        assertThat(leg.getCreatedAt()).isEqualTo(NOW);
        assertThat(leg.getUpdatedAt()).isEqualTo(NOW);
    }

    @Test
    void requestGivesEachLegItsOwnIdAndRequestEventId() {
        DepositLeg first = DepositLeg.request(CONTRACT_ID, DepositLegParty.BUYER, BUYER_ID, AMOUNT, NOW);
        DepositLeg second = DepositLeg.request(CONTRACT_ID, DepositLegParty.SELLER, BUYER_ID, AMOUNT, NOW);

        assertThat(UUID.fromString(first.getLegId())).isNotNull();
        assertThat(UUID.fromString(first.getRequestEventId())).isNotNull();
        assertThat(first.getLegId()).isNotEqualTo(first.getRequestEventId());
        assertThat(first.getLegId()).isNotEqualTo(second.getLegId());
        assertThat(first.getRequestEventId()).isNotEqualTo(second.getRequestEventId());
    }

    @Test
    void rejectsAContractIdThatIsNotAUuid() {
        assertThatThrownBy(() -> DepositLeg.request("contract-1", DepositLegParty.BUYER, BUYER_ID, AMOUNT, NOW))
                .isInstanceOf(InvalidDepositLegException.class).hasMessageContaining("contractId");
    }

    @Test
    void rejectsAMissingUserId() {
        assertThatThrownBy(() -> DepositLeg.request(CONTRACT_ID, DepositLegParty.BUYER, null, AMOUNT, NOW))
                .isInstanceOf(InvalidDepositLegException.class).hasMessageContaining("userId");
    }

    @Test
    void rejectsMissingPartyAmountOrTime() {
        assertThatThrownBy(() -> DepositLeg.request(CONTRACT_ID, null, BUYER_ID, AMOUNT, NOW))
                .isInstanceOf(InvalidDepositLegException.class);
        assertThatThrownBy(() -> DepositLeg.request(CONTRACT_ID, DepositLegParty.BUYER, BUYER_ID, null, NOW))
                .isInstanceOf(InvalidDepositLegException.class);
        assertThatThrownBy(() -> DepositLeg.request(CONTRACT_ID, DepositLegParty.BUYER, BUYER_ID, AMOUNT, null))
                .isInstanceOf(InvalidDepositLegException.class);
    }

    @Test
    void moneyRejectsZeroAndExtraDecimals() {
        assertThatThrownBy(() -> new Money(BigDecimal.ZERO)).isInstanceOf(InvalidDepositLegException.class);
        assertThatThrownBy(() -> new Money(new BigDecimal("10.005"))).isInstanceOf(InvalidDepositLegException.class);
        assertThat(new Money(new BigDecimal("10.5"))).isEqualTo(new Money(new BigDecimal("10.50")));
    }
}
