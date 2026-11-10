package com.agricontract.product.application.usecase;

import com.agricontract.product.application.dto.ContractLifecycleCommand;
import com.agricontract.product.application.exception.ReservationNotReadyException;
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
 * Reservation transitions driven by contract lifecycle events (design §8b.2, matrix 11s/28b):
 * <ul>
 *   <li>contract.signed: RESERVED -> SIGNED_RELEASABLE. Signed is not the commit point.</li>
 *   <li>contract.activated: SIGNED_RELEASABLE -> COMMITTED, the hard commit point. Before contract.signed was
 *       applied (separate queues, no order between them) it is retried, never forced past SIGNED_RELEASABLE.</li>
 *   <li>contract.activation_failed: SIGNED_RELEASABLE (or RESERVED, if signed is still in flight) -> RELEASED.</li>
 *   <li>contract.withdrawn: RESERVED -> RELEASED.</li>
 * </ul>
 * One transaction per event: lock the reservation, dedup by eventId, one conditional transition. A release restores
 * the quantity in that transaction, so a replay or a concurrent compensation restores it at most once. A state the
 * event cannot follow (activation after release, release after commit, ...) is an inventory incident: logged and
 * alerted once, recorded so redelivery stays quiet, never a reverse transition or a second inventory path.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ApplyContractLifecycleEventUseCaseImpl implements ApplyContractLifecycleEventUseCase {

    private final ReservationRepository reservationRepository;
    private final ProcessedContractEventRepository processedContractEventRepository;
    private final ReservationReleaser reservationReleaser;
    private final TransactionTemplate transactionTemplate;

    @Override
    public void execute(ContractLifecycleCommand command) {
        if (command.eventType() == ContractLifecycleEventType.CONTRACT_CREATED) {
            throw new IllegalArgumentException("contract.created is handled by ConfirmContractCreatedUseCase");
        }
        transactionTemplate.executeWithoutResult(status -> apply(command));
    }

    private void apply(ContractLifecycleCommand command) {
        ContractId contractId = new ContractId(command.contractId());
        // lock before the dedup check, same order as contract.created and the reaper
        Optional<Reservation> locked = reservationRepository.lockByContractId(contractId);
        if (processedContractEventRepository.exists(command.eventId())) {
            log.debug("Skipping duplicate {} {} for contract {}", command.eventType().wireName(), command.eventId(),
                    contractId.value());
            return;
        }

        if (locked.isEmpty()) {
            incident(command, "without_reservation", null);
        } else if (!locked.get().isSameRequest(new ListingId(command.listingId()), command.reservedQuantity())) {
            Reservation current = locked.get();
            log.error("event={}_reservation_mismatch alert=OPERATOR contractId={} eventId={} eventListingId={}"
                            + " eventQuantity={} listingId={} quantity={}", eventName(command), contractId.value(),
                    command.eventId(), command.listingId(), command.reservedQuantity(),
                    current.getListingId().value(), current.getReservedQuantity());
        } else {
            switch (command.eventType()) {
                case CONTRACT_SIGNED -> signed(command, locked.get());
                case CONTRACT_ACTIVATED -> activated(command, locked.get());
                case CONTRACT_ACTIVATION_FAILED -> activationFailed(command, locked.get());
                case CONTRACT_WITHDRAWN -> withdrawn(command, locked.get());
                default -> throw new IllegalStateException("Unhandled " + command.eventType());
            }
        }
        processedContractEventRepository.record(command.eventId(), command.eventType(), contractId);
    }

    private void signed(ContractLifecycleCommand command, Reservation reservation) {
        switch (reservation.getStatus()) {
            case RESERVED -> move(command, reservation, ReservationStatus.SIGNED_RELEASABLE);
            case SIGNED_RELEASABLE, COMMITTED -> alreadyPast(command, reservation);
            case RELEASED -> {
                // activation_failed may have overtaken signed on its own queue: the release was legitimate
                if (processedContractEventRepository.hasRecorded(reservation.getContractId(),
                        ContractLifecycleEventType.CONTRACT_ACTIVATION_FAILED)) {
                    alreadyPast(command, reservation);
                } else {
                    incident(command, "after_release", reservation);
                }
            }
        }
    }

    private void activated(ContractLifecycleCommand command, Reservation reservation) {
        switch (reservation.getStatus()) {
            case SIGNED_RELEASABLE -> move(command, reservation, ReservationStatus.COMMITTED);
            case COMMITTED -> alreadyPast(command, reservation);
            case RESERVED -> throw new ReservationNotReadyException("contract.activated for contract "
                    + reservation.getContractId().value() + " before contract.signed was applied");
            case RELEASED -> incident(command, "after_release", reservation);
        }
    }

    private void activationFailed(ContractLifecycleCommand command, Reservation reservation) {
        switch (reservation.getStatus()) {
            case RESERVED, SIGNED_RELEASABLE -> release(command, reservation);
            case RELEASED -> alreadyPast(command, reservation);
            // committed inventory is never restored (design §8b.2)
            case COMMITTED -> incident(command, "after_commit", reservation);
        }
    }

    private void withdrawn(ContractLifecycleCommand command, Reservation reservation) {
        switch (reservation.getStatus()) {
            case RESERVED -> release(command, reservation);
            // e.g. the reaper or a create compensation released it first
            case RELEASED -> alreadyPast(command, reservation);
            // a withdraw is only possible before the second signature
            case SIGNED_RELEASABLE, COMMITTED -> incident(command, "after_sign", reservation);
        }
    }

    private void move(ContractLifecycleCommand command, Reservation reservation, ReservationStatus target) {
        // the row is locked, so the conditional transition cannot lose a race here
        if (!reservationRepository.transition(reservation.getContractId(), reservation.getStatus(), target)) {
            throw new IllegalStateException("Reservation " + reservation.getContractId().value()
                    + " changed under its row lock");
        }
        log.info("Reservation for contract {} {} -> {} ({} {})", reservation.getContractId().value(),
                reservation.getStatus(), target, command.eventType().wireName(), command.eventId());
    }

    private void release(ContractLifecycleCommand command, Reservation reservation) {
        if (!reservationReleaser.release(reservation, reservation.getStatus())) {
            throw new IllegalStateException("Reservation " + reservation.getContractId().value()
                    + " changed under its row lock");
        }
        log.info("Released reservation for contract {} on listing {} ({}) on {} {}",
                reservation.getContractId().value(), reservation.getListingId().value(),
                reservation.getReservedQuantity(), command.eventType().wireName(), command.eventId());
    }

    private static void alreadyPast(ContractLifecycleCommand command, Reservation reservation) {
        log.info("{} {} for contract {}: reservation already {}, nothing to do", command.eventType().wireName(),
                command.eventId(), reservation.getContractId().value(), reservation.getStatus());
    }

    private static void incident(ContractLifecycleCommand command, String what, Reservation reservation) {
        log.error("event={}_{} alert=OPERATOR contractId={} eventId={} status={}: resolve as an inventory incident",
                eventName(command), what, command.contractId(), command.eventId(),
                reservation == null ? null : reservation.getStatus());
    }

    /** contract.activation_failed -> contract_activation_failed, for log keys. */
    private static String eventName(ContractLifecycleCommand command) {
        return command.eventType().wireName().replace('.', '_');
    }
}
