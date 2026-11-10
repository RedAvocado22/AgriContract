package com.agricontract.contract.application.service;

import com.agricontract.contract.application.port.ListingPort;
import com.agricontract.contract.domain.exception.ContractConflictException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * Gives a reservation back when the create that took it could not persist the contract (design §2.1d step 4).
 * The release is idempotent by contractId on product-service's side, so it is repeated with backoff until it is
 * answered. If product-service stays unreachable, OrphanReservationReaperJob (product design §8b.2) releases the
 * reservation once no contract.created arrived within its grace period; the log line tells OPERATOR.
 */
@Service
@Slf4j
public class ReservationCompensation {

    private final ListingPort listingPort;
    private final int attempts;
    private final long backoffMillis;

    public ReservationCompensation(ListingPort listingPort,
                                   @Value("${contract.create.compensation-attempts:3}") int attempts,
                                   @Value("${contract.create.compensation-backoff-ms:500}") long backoffMillis) {
        if (attempts < 1) {
            throw new IllegalArgumentException("contract.create.compensation-attempts must be at least 1");
        }
        this.listingPort = listingPort;
        this.attempts = attempts;
        this.backoffMillis = backoffMillis;
    }

    /** Never throws: the caller reports the failure that made the compensation necessary. */
    public void release(String contractId, String listingId, BigDecimal reservedQuantity) {
        for (int attempt = 1; ; attempt++) {
            try {
                listingPort.releaseReservation(contractId, listingId, reservedQuantity);
                log.info("Released reservation of unpersisted contract {} on listing {} ({} kg)", contractId,
                        listingId, reservedQuantity);
                return;
            } catch (ContractConflictException conflict) {
                log.error("event=create_compensation_conflict alert=OPERATOR contractId={} listingId={} code={} {}",
                        contractId, listingId, conflict.getCode(), conflict.getMessage());
                return;
            } catch (RuntimeException failed) {
                if (attempt >= attempts) {
                    log.error("event=create_compensation_failed alert=OPERATOR contractId={} listingId={} "
                                    + "reservedQuantity={} attempts={}: left to OrphanReservationReaperJob",
                            contractId, listingId, reservedQuantity, attempts, failed);
                    return;
                }
                log.warn("Release of reservation {} failed (attempt {}/{}): {}", contractId, attempt, attempts,
                        failed.getMessage());
                if (!pause(backoffMillis << (attempt - 1))) {
                    log.error("event=create_compensation_interrupted alert=OPERATOR contractId={} listingId={}",
                            contractId, listingId);
                    return;
                }
            }
        }
    }

    private static boolean pause(long millis) {
        if (millis <= 0) {
            return true;
        }
        try {
            Thread.sleep(millis);
            return true;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
