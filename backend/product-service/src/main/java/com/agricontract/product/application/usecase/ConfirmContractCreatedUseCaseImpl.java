package com.agricontract.product.application.usecase;

import com.agricontract.product.application.dto.ContractCreatedCommand;
import com.agricontract.product.domain.model.Reservation;
import com.agricontract.product.domain.model.vo.ContractId;
import com.agricontract.product.domain.model.vo.ContractLifecycleEventType;
import com.agricontract.product.domain.model.vo.ListingId;
import com.agricontract.product.domain.model.vo.ReservationStatus;
import com.agricontract.product.domain.repository.ProcessedContractEventRepository;
import com.agricontract.product.domain.repository.ReservationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Optional;

/**
 * contract.created only confirms the reservation is attached to a persisted contract: it never decrements again
 * and never changes reservation state (design §8b.2). Recording it is what keeps the orphan reaper away.
 * Arriving after the reservation was released (reaper or compensation) is an inventory incident: a live contract
 * whose stock was restored. That is logged and alerted once — the eventId is still recorded so redelivery stays
 * quiet — with no reverse transition, re-decrement or second inventory path.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConfirmContractCreatedUseCaseImpl implements ConfirmContractCreatedUseCase {

    private final ReservationRepository reservationRepository;
    private final ProcessedContractEventRepository processedContractEventRepository;
    private final TransactionTemplate transactionTemplate;

    @Override
    public void execute(ContractCreatedCommand command) {
        transactionTemplate.executeWithoutResult(status -> confirm(command));
    }

    private void confirm(ContractCreatedCommand command) {
        ContractId contractId = new ContractId(command.contractId());
        // lock before recording, same order as the reaper, so the reaper either sees this event or released first
        Optional<Reservation> reservation = reservationRepository.lockByContractId(contractId);
        if (processedContractEventRepository.exists(command.eventId())) {
            log.debug("Skipping duplicate contract.created {} for contract {}", command.eventId(), contractId.value());
            return;
        }
        processedContractEventRepository.record(command.eventId(), ContractLifecycleEventType.CONTRACT_CREATED,
                contractId);

        if (reservation.isEmpty()) {
            log.error("event=contract_created_without_reservation alert=OPERATOR contractId={} eventId={}",
                    contractId.value(), command.eventId());
            return;
        }
        Reservation current = reservation.get();
        if (!current.isSameRequest(new ListingId(command.listingId()), command.reservedQuantity())) {
            log.error("event=contract_created_reservation_mismatch alert=OPERATOR contractId={} eventId={}"
                            + " eventListingId={} eventQuantity={} listingId={} quantity={}",
                    contractId.value(), command.eventId(), command.listingId(), command.reservedQuantity(),
                    current.getListingId().value(), current.getReservedQuantity());
            return;
        }
        if (current.getStatus() == ReservationStatus.RELEASED) {
            log.error("event=contract_created_after_release alert=OPERATOR contractId={} eventId={} listingId={}"
                            + " quantity={}: live contract on restored inventory, resolve as an inventory incident",
                    contractId.value(), command.eventId(), current.getListingId().value(),
                    current.getReservedQuantity());
            return;
        }
        log.info("Reservation for contract {} attached to a persisted contract ({})",
                contractId.value(), current.getStatus());
    }
}
