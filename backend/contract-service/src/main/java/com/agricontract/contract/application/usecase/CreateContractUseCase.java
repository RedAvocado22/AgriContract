package com.agricontract.contract.application.usecase;

import com.agricontract.contract.application.dto.CoffeeContractView;
import com.agricontract.contract.application.dto.CreateContractCommand;
import com.agricontract.contract.application.dto.ListingReservation;
import com.agricontract.contract.application.dto.ListingResponse;
import com.agricontract.contract.application.dto.UserInfo;
import com.agricontract.contract.application.exception.IdempotencyKeyReusedException;
import com.agricontract.contract.application.exception.UnauthorizedContractActionException;
import com.agricontract.contract.application.port.ContractEventPublisher;
import com.agricontract.contract.application.port.IdempotencyStore;
import com.agricontract.contract.application.port.ListingPort;
import com.agricontract.contract.application.port.UserPort;
import com.agricontract.contract.application.service.ReservationCompensation;
import com.agricontract.contract.domain.exception.ContractConflictException;
import com.agricontract.contract.domain.model.Contract;
import com.agricontract.contract.domain.model.terms.CanonicalTerms;
import com.agricontract.contract.domain.model.terms.CoffeeGoodsTerms;
import com.agricontract.contract.domain.model.terms.ContractTemplateProfile;
import com.agricontract.contract.domain.model.terms.ContractTermsRules;
import com.agricontract.contract.domain.model.terms.ContractTermsSnapshot;
import com.agricontract.contract.domain.model.terms.InvalidContractTermsException;
import com.agricontract.contract.domain.model.terms.MilestoneTerm;
import com.agricontract.contract.domain.model.terms.TermsCanonicalizer;
import com.agricontract.contract.domain.model.vo.ContractId;
import com.agricontract.contract.domain.repository.ContractRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * POST /api/v1/contracts (createContract, milestone-escrow design §2.1d). In this order:
 * <ol>
 *   <li>validate the negotiated terms and derive requestedQuantity = Σ milestoneSchedule[].committedQuantity;</li>
 *   <li>read the seller, both profiles and the goods snapshot at the buyer's listing version (no write yet);</li>
 *   <li>reserve the quantity at product-service under a newly generated contractId;</li>
 *   <li>in one local transaction: claim the Idempotency-Key, persist OFFERED with reservedQuantity and append
 *       contract.created to the outbox;</li>
 *   <li>if that transaction fails, release the reservation (idempotent compensation) and report the failure.</li>
 * </ol>
 * A dependency that does not answer fails the create closed: no OFFERED row exists without a reservation.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CreateContractUseCase {
    static final String OPERATION = "CREATE_CONTRACT";
    /** Same scale as reserved_quantity here and listing_reservations.reserved_quantity at product-service. */
    private static final int MAX_QUANTITY_SCALE = 3;

    private final ContractRepository contractRepository;
    private final IdempotencyStore idempotencyStore;
    private final ListingPort listingPort;
    private final UserPort userPort;
    private final ReservationCompensation compensation;
    private final ContractEventPublisher events;
    private final TermsCanonicalizer canonicalizer;
    private final ContractTermsRules rules;
    private final ContractTemplateProfile currentTemplate;
    private final TransactionTemplate tx;
    private final Clock clock;

    public Result execute(CreateContractCommand command) {
        // 1. Everything the request alone can get wrong is refused before any remote call.
        rules.validateNegotiated(command.terms());
        BigDecimal requestedQuantity = requestedQuantity(command);
        String requestHash = CanonicalTerms.sha256(command.listingId() + "\n" + command.listingVersionToken() + "\n"
                + canonicalizer.canonicalInputJson(command.terms()));

        // Fast path for a retry; a concurrent first request is caught by the claim in step 4.
        Optional<IdempotencyStore.Outcome> seen = tx.execute(status -> idempotencyStore.lookup(command.buyerId(),
                OPERATION, command.idempotencyKey(), requestHash));
        if (seen.isPresent()) {
            return replay(command, seen.get());
        }

        // 2. Reads only: a failure here leaves nothing to undo.
        ListingResponse listing = listingPort.getListing(command.listingId());
        if (command.buyerId().equals(listing.sellerId())) {
            throw new UnauthorizedContractActionException("A seller cannot make an offer on their own listing.");
        }
        UserInfo buyer = profile(command.buyerId(), "BUYER");
        UserInfo seller = profile(listing.sellerId(), "SELLER");
        CoffeeGoodsTerms goods = listingPort.contractSnapshot(command.listingId(), command.listingVersionToken());
        if (goods == null || goods.listingId() == null || !goods.listingId().toString().equals(command.listingId())) {
            throw new ContractConflictException("LISTING_STATE_CONFLICT",
                    "product-service returned goods terms of another listing; reload the listing.");
        }
        // Coffee only (matrix 22, 27j): validateGoods refuses any other commodity or shape with a 400.
        CanonicalTerms terms = CanonicalTerms.of(
                ContractTermsSnapshot.assemble(command.terms(), goods, currentTemplate, rules), canonicalizer);

        // 3. Reserve. The contractId exists from here on, at product-service only.
        String contractId = UUID.randomUUID().toString();
        ListingReservation reservation;
        try {
            reservation = listingPort.reserve(contractId, command.listingId(), requestedQuantity,
                    command.listingVersionToken());
        } catch (ContractConflictException refused) {
            throw refused; // 409: nothing was reserved.
        } catch (RuntimeException unknownOutcome) {
            // Timeout or 5xx: product-service may have committed the reserve before failing to answer.
            compensation.release(contractId, command.listingId(), requestedQuantity);
            throw unknownOutcome;
        }

        // 4. Local transaction; 5. compensation if it does not commit.
        try {
            requireReservationMatches(reservation, contractId, command.listingId(), requestedQuantity);
            Instant createdAt = clock.instant();
            Contract contract = Contract.offerCoffee(new ContractId(contractId), command.listingId(),
                    command.buyerId(), listing.sellerId(), buyer.organizationName(), seller.organizationName(),
                    buyer.email(), seller.email(), terms);
            CoffeeContractView view = tx.execute(status -> persist(command, requestHash, contract, createdAt));
            log.info("Contract {} offered: buyer={} seller={} listing={} reserved={} kg", contractId,
                    command.buyerId(), listing.sellerId(), command.listingId(), requestedQuantity);
            return new Result(view, false);
        } catch (ConcurrentCreate raced) {
            compensation.release(contractId, command.listingId(), requestedQuantity);
            return replay(command, raced.outcome);
        } catch (RuntimeException failed) {
            compensation.release(contractId, command.listingId(), requestedQuantity);
            throw failed;
        }
    }

    private CoffeeContractView persist(CreateContractCommand command, String requestHash, Contract contract,
                                       Instant createdAt) {
        IdempotencyStore.Outcome outcome = idempotencyStore.claim(command.buyerId(), OPERATION,
                command.idempotencyKey(), requestHash);
        if (outcome != IdempotencyStore.Outcome.FIRST) {
            // A concurrent request with the same key committed first: roll back, give this reservation back.
            throw new ConcurrentCreate(outcome);
        }
        contractRepository.save(contract);
        events.contractCreated(contract, createdAt);
        idempotencyStore.recordResult(command.buyerId(), OPERATION, command.idempotencyKey(),
                contract.getContractId().value());
        return CoffeeContractView.from(contract);
    }

    private Result replay(CreateContractCommand command, IdempotencyStore.Outcome outcome) {
        if (outcome == IdempotencyStore.Outcome.CONFLICT) {
            throw new IdempotencyKeyReusedException();
        }
        String contractId = idempotencyStore.resultOf(command.buyerId(), OPERATION, command.idempotencyKey())
                .orElseThrow(() -> new IllegalStateException("Create " + command.idempotencyKey()
                        + " was recorded without its contract"));
        Contract contract = contractRepository.findById(new ContractId(contractId))
                .orElseThrow(() -> new IllegalStateException("Contract " + contractId + " of create "
                        + command.idempotencyKey() + " is missing"));
        log.info("Replayed create {} for contract {}", command.idempotencyKey(), contractId);
        return new Result(CoffeeContractView.from(contract), true);
    }

    private static BigDecimal requestedQuantity(CreateContractCommand command) {
        BigDecimal total = command.terms().milestoneSchedule().stream()
                .map(MilestoneTerm::committedQuantity)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (total.stripTrailingZeros().scale() > MAX_QUANTITY_SCALE) {
            throw new InvalidContractTermsException("committedQuantity allows at most " + MAX_QUANTITY_SCALE
                    + " decimal places (kg)");
        }
        return total;
    }

    private UserInfo profile(String userId, String role) {
        UserInfo user = userPort.findUser(userId).orElseThrow(() -> new ContractConflictException(
                "PARTY_PROFILE_NOT_FOUND", "No " + role + " profile was found for this offer."));
        if (user.email() == null || user.email().isBlank()
                || user.organizationName() == null || user.organizationName().isBlank()) {
            throw new ContractConflictException("PARTY_PROFILE_INCOMPLETE",
                    "The " + role + " profile has no email or organization name to put on the contract.");
        }
        return user;
    }

    private static void requireReservationMatches(ListingReservation reservation, String contractId,
                                                  String listingId, BigDecimal requestedQuantity) {
        if (reservation == null || !contractId.equals(reservation.contractId())
                || !listingId.equals(reservation.listingId())
                || reservation.reservedQuantity() == null
                || reservation.reservedQuantity().compareTo(requestedQuantity) != 0
                || !"RESERVED".equals(reservation.state())) {
            throw new IllegalStateException("product-service answered reservation " + reservation
                    + " for contract " + contractId + ", expected RESERVED " + requestedQuantity + " kg");
        }
    }

    /** replayed: an earlier request with this Idempotency-Key created the contract. */
    public record Result(CoffeeContractView contract, boolean replayed) {
    }

    /** Thrown inside the transaction to roll it back when the same key committed concurrently. */
    private static final class ConcurrentCreate extends RuntimeException {
        private final transient IdempotencyStore.Outcome outcome;

        private ConcurrentCreate(IdempotencyStore.Outcome outcome) {
            super(null, null, false, false);
            this.outcome = outcome;
        }
    }
}
