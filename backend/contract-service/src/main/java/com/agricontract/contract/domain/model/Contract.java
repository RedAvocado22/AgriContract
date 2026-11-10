package com.agricontract.contract.domain.model;

import com.agricontract.contract.domain.event.*;
import com.agricontract.contract.domain.exception.ContractConflictException;
import com.agricontract.contract.domain.exception.UnauthorizedContractAccessException;
import com.agricontract.contract.domain.model.consent.ConsentActionType;
import com.agricontract.contract.domain.model.consent.ConsentEvidence;
import com.agricontract.contract.domain.model.consent.ConsentStatement;
import com.agricontract.contract.domain.model.consent.ConsentStatements;
import com.agricontract.contract.domain.model.consent.ConsentSubjectType;
import com.agricontract.contract.domain.model.consent.SignerRole;
import com.agricontract.contract.domain.model.signature.OtpChallenge;
import com.agricontract.contract.domain.model.signature.Signature;
import com.agricontract.contract.domain.model.vo.ContractId;
import com.agricontract.contract.domain.model.vo.ContractStatus;
import com.agricontract.contract.domain.model.vo.ContractTerms;
import com.agricontract.contract.domain.model.vo.Money;
import com.agricontract.contract.domain.model.vo.Quantity;
import com.agricontract.contract.domain.model.terms.CanonicalTerms;
import com.agricontract.contract.domain.model.terms.ContractTermsSnapshot;
import com.agricontract.contract.domain.model.terms.InvalidContractTermsException;
import com.agricontract.contract.domain.model.terms.MilestoneTerm;
import lombok.AccessLevel;
import lombok.Getter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

// Aggregate Root
// State machine (Phase 2, milestone-escrow design §3.1): OFFERED → NEGOTIATING → SIGNED → ACTIVE → SETTLED.
// Phase 1 delivery/cancel/dispute paths are removed; Phase 2 termination and milestones replace them.
@Getter
public class Contract {

    private ContractId contractId;
    private String listingId;
    private String sellerId;
    private String buyerId;
    private String productName;       // snapshot at offer time
    private String buyerOrgName;      // snapshot at offer time
    private String sellerOrgName;     // snapshot at offer time
    private String buyerEmail;        // snapshot at offer time
    private String sellerEmail;       // snapshot at offer time
    private ContractTerms terms;
    /** Phase 2 coffee terms + signedContentHash; null only for Phase 1 rows created before #92. */
    private CanonicalTerms coffeeTerms;
    private ContractStatus status;
    /** Optimistic-lock version as loaded; null for a contract not stored yet. */
    private Long version;
    /** signedContentHash per party that has signed, loaded from the signature table (the only source). */
    @Getter(AccessLevel.NONE)
    private Map<SignerRole, String> signedHashes;
    @Getter(AccessLevel.NONE)
    private List<DomainEvent> domainEvents;

    private Contract() {
        this.signedHashes = new EnumMap<>(SignerRole.class);
        this.domainEvents = new ArrayList<>();
    }

    public static Contract reconstitute(
            ContractId contractId, String listingId,
            String buyerId, String sellerId,
            String productName, String buyerOrgName, String sellerOrgName,
            String buyerEmail, String sellerEmail,
            ContractTerms terms, CanonicalTerms coffeeTerms, ContractStatus status,
            Map<SignerRole, String> signedHashes, Long version) {
        Contract contract = new Contract();
        contract.coffeeTerms = coffeeTerms;
        contract.version = version;

        contract.contractId = contractId;
        contract.listingId = listingId;
        contract.productName = productName;
        contract.buyerId = buyerId;
        contract.buyerOrgName = buyerOrgName;
        contract.buyerEmail = buyerEmail;
        contract.sellerId = sellerId;
        contract.sellerOrgName = sellerOrgName;
        contract.sellerEmail = sellerEmail;
        contract.terms = terms;
        contract.status = status;
        contract.signedHashes.putAll(signedHashes);

        return contract;
    }

    public static Contract offer(ContractId contractId, String listingId,
                                 String buyerId, String sellerId,
                                 String productName, String buyerOrgName, String sellerOrgName,
                                 String buyerEmail, String sellerEmail,
                                 ContractTerms terms) {
        Contract contract = new Contract();
        contract.contractId = contractId;
        contract.listingId = listingId;
        contract.buyerId = buyerId;
        contract.sellerId = sellerId;
        contract.buyerOrgName = buyerOrgName;
        contract.sellerOrgName = sellerOrgName;
        contract.buyerEmail = buyerEmail;
        contract.sellerEmail = sellerEmail;
        contract.terms = terms;
        contract.status = ContractStatus.OFFERED;
        contract.productName = productName;

        contract.domainEvents.add(new ContractOfferedEvent(contractId.value(), buyerEmail, sellerEmail, buyerId, sellerId, listingId, terms));

        return contract;
    }

    /**
     * Phase 2 offer with full coffee terms. The Phase 1 columns are kept filled as a read projection of the
     * signed terms until the Phase 1 flow is removed.
     */
    public static Contract offerCoffee(ContractId contractId, String listingId,
                                       String buyerId, String sellerId,
                                       String buyerOrgName, String sellerOrgName,
                                       String buyerEmail, String sellerEmail,
                                       CanonicalTerms coffeeTerms) {
        ContractTermsSnapshot snapshot = coffeeTerms.snapshot();
        Contract contract = offer(contractId, listingId, buyerId, sellerId,
                snapshot.goodsTerms().productName(), buyerOrgName, sellerOrgName, buyerEmail, sellerEmail,
                legacyProjection(snapshot));
        contract.coffeeTerms = coffeeTerms;
        return contract;
    }

    /**
     * PATCH terms (#93). Either party may revise while OFFERED/NEGOTIATING, until the first valid signature.
     * The total kg must stay equal to what product-service reserved for this contract (F18): changing the total
     * means withdrawing and making a new offer. goodsTerms come from the stored snapshot, never from the caller.
     */
    public void reviseCoffeeTerms(String userId, CanonicalTerms revised) {
        //Guard
        if (!userId.equals(sellerId) && !userId.equals(buyerId)) {
            throw new UnauthorizedContractAccessException("This user doesn't have right to access.");
        }
        if (this.coffeeTerms == null) {
            throw new ContractConflictException(ContractConflictException.LEGACY_TERMS,
                    "This contract has no coffee terms; create a new offer instead.");
        }
        if (this.status != ContractStatus.OFFERED && this.status != ContractStatus.NEGOTIATING) {
            throw new ContractConflictException(ContractConflictException.NOT_NEGOTIABLE,
                    "Terms can only change while the contract is OFFERED or NEGOTIATING, not " + this.status + ".");
        }
        if (!this.signedHashes.isEmpty()) {
            throw new ContractConflictException(ContractConflictException.TERMS_FROZEN,
                    "Terms are frozen after the first signature.");
        }
        ContractTermsSnapshot current = this.coffeeTerms.snapshot();
        if (!revised.snapshot().goodsTerms().equals(current.goodsTerms())) {
            throw new InvalidContractTermsException("goodsTerms are fixed at offer time and cannot be revised");
        }
        if (revised.snapshot().totalQuantity().compareTo(current.totalQuantity()) != 0) {
            throw new InvalidContractTermsException("Total committed quantity must stay "
                    + current.totalQuantity().stripTrailingZeros().toPlainString()
                    + " kg, the quantity reserved for this offer; withdraw and make a new offer to change it");
        }
        //Mutate
        this.coffeeTerms = revised;
        this.terms = legacyProjection(revised.snapshot());
        this.status = ContractStatus.NEGOTIATING;
    }

    /**
     * Checkbox consent before an OTP challenge (#94). Refuses, without any side effect, when the caller is not
     * the party it claims to be, the contract can no longer be signed or already carries this party's signature,
     * the statement version is not the one in force, or the caller is looking at other terms than the current
     * ones. Returns the evidence to append in the same transaction as the OTP challenge.
     */
    /**
     * D42: a party ticks the CANCEL_ACTIVATION statement while looking at the signed terms. Only for a SIGNED coffee
     * contract; whether its deposit lock failed is the caller's check (activation recovery lives outside the aggregate).
     */
    public ConsentEvidence consentToCancelActivation(String userId, SignerRole claimedRole,
                                                     String presentedStatementVersion, String presentedContentHash,
                                                     ConsentStatements statements, UUID consentId,
                                                     Instant consentedAt, String correlationId) {
        String partyId = claimedRole == SignerRole.BUYER ? buyerId : sellerId;
        if (!userId.equals(partyId)) {
            throw new UnauthorizedContractAccessException("This user is not the " + claimedRole + " of this contract.");
        }
        if (this.coffeeTerms == null || this.status != ContractStatus.SIGNED) {
            throw new ContractConflictException(ContractConflictException.NOT_CANCELLABLE,
                    "Only a SIGNED contract waiting for its deposits can be cancelled, not " + this.status + ".");
        }
        ConsentStatement statement = statements.requireActiveCancelActivation(claimedRole, presentedStatementVersion);
        if (!this.coffeeTerms.signedContentHash().equals(presentedContentHash)) {
            throw new ContractConflictException(ContractConflictException.PRESENTED_CONTENT_STALE,
                    "The fingerprint shown is not this contract's; reload the page.");
        }
        return new ConsentEvidence(consentId, ConsentActionType.CANCEL_ACTIVATION, ConsentSubjectType.CONTRACT,
                this.contractId.value(), userId, claimedRole, statement.statementCode(),
                statement.statementVersion(), statement.statementHash(), presentedContentHash, consentedAt,
                correlationId);
    }

    public ConsentEvidence consentToSign(String userId, SignerRole claimedRole, String presentedStatementVersion,
                                         String presentedContentHash, ConsentStatements statements,
                                         UUID consentId, Instant consentedAt, String correlationId) {
        //Guard
        String partyId = claimedRole == SignerRole.BUYER ? buyerId : sellerId;
        if (!userId.equals(partyId)) {
            throw new UnauthorizedContractAccessException("This user is not the " + claimedRole + " of this contract.");
        }
        if (this.coffeeTerms == null) {
            throw new ContractConflictException(ContractConflictException.LEGACY_TERMS,
                    "This contract has no coffee terms; create a new offer instead.");
        }
        if (this.status != ContractStatus.OFFERED && this.status != ContractStatus.NEGOTIATING) {
            throw new ContractConflictException(ContractConflictException.NOT_SIGNABLE,
                    "Only an OFFERED or NEGOTIATING contract can be signed, not " + this.status + ".");
        }
        if (this.signedHashes.containsKey(claimedRole)) {
            throw new ContractConflictException(ContractConflictException.ALREADY_SIGNED,
                    "This party has already signed.");
        }
        ConsentStatement statement = statements.requireActiveSignContract(claimedRole, presentedStatementVersion);
        if (!this.coffeeTerms.signedContentHash().equals(presentedContentHash)) {
            throw new ContractConflictException(ContractConflictException.PRESENTED_CONTENT_STALE,
                    "The terms changed since this page was loaded; reload and review them before signing.");
        }
        //Record
        return new ConsentEvidence(consentId, ConsentActionType.SIGN_CONTRACT, ConsentSubjectType.CONTRACT,
                this.contractId.value(), userId, claimedRole, statement.statementCode(),
                statement.statementVersion(), statement.statementHash(), presentedContentHash, consentedAt,
                correlationId);
    }

    private static ContractTerms legacyProjection(ContractTermsSnapshot snapshot) {
        var negotiated = snapshot.negotiated();
        var lastDelivery = negotiated.milestoneSchedule().stream()
                .map(MilestoneTerm::expectedDeliveryDate)
                .max(Comparator.naturalOrder())
                .orElseThrow();
        return new ContractTerms(
                new Quantity(snapshot.totalQuantity(), "KG"),
                new Money(negotiated.agreedPrice().value(), negotiated.currency()),
                lastDelivery,
                negotiated.buyerPenaltyRate(),
                negotiated.sellerDepositRate(),
                null);
    }

    public void counterOffer(String userId, ContractTerms newTerms) {
        //Guard
        if (!userId.equals(this.sellerId) && !userId.equals(this.buyerId)) {
            throw new UnauthorizedContractAccessException("This user doesn't have right to access.");
        }

        if (this.status != ContractStatus.OFFERED && this.status != ContractStatus.NEGOTIATING) {
            throw new IllegalArgumentException("This contract can't be update.");
        }

        //Mutate
        this.status = ContractStatus.NEGOTIATING;
        this.terms = newTerms;

        //Emit
        this.domainEvents.add(new ContractNegotiatingEvent(this.contractId.value(), this.buyerEmail, this.sellerEmail, userId, newTerms));
    }

    /** The party this user is on this contract, empty for anyone else. */
    public Optional<SignerRole> roleOf(String userId) {
        if (userId.equals(buyerId)) {
            return Optional.of(SignerRole.BUYER);
        }
        if (userId.equals(sellerId)) {
            return Optional.of(SignerRole.SELLER);
        }
        return Optional.empty();
    }

    /**
     * Records one party's signature once its OTP is verified (#97, signature design §6 steps 4-5). The challenge
     * must be for these exact terms; the second signature must be on the same hash as the first, and then the
     * contract becomes SIGNED. Throws, without any change, when the contract can no longer be signed.
     */
    public Signature sign(String userId, OtpChallenge verified, Instant authTime, Instant signedAt,
                          String ipAddress, UUID signatureId) {
        //Guard
        SignerRole role = verified.signerRole();
        String partyId = role == SignerRole.BUYER ? buyerId : sellerId;
        if (!userId.equals(partyId) || !userId.equals(verified.userId())) {
            throw new UnauthorizedContractAccessException("This user is not the " + role + " of this contract.");
        }
        if (!verified.contractId().equals(this.contractId.value())) {
            throw new IllegalStateException("Challenge " + verified.otpId() + " is not for contract "
                    + this.contractId.value());
        }
        if (this.coffeeTerms == null) {
            throw new ContractConflictException(ContractConflictException.LEGACY_TERMS,
                    "This contract has no coffee terms; create a new offer instead.");
        }
        if (this.status != ContractStatus.OFFERED && this.status != ContractStatus.NEGOTIATING) {
            throw new ContractConflictException(ContractConflictException.NOT_SIGNABLE,
                    "Only an OFFERED or NEGOTIATING contract can be signed, not " + this.status + ".");
        }
        if (this.signedHashes.containsKey(role)) {
            throw new ContractConflictException(ContractConflictException.ALREADY_SIGNED,
                    "This party has already signed.");
        }
        String hash = this.coffeeTerms.signedContentHash();
        if (!hash.equals(verified.signedContentHash())) {
            throw new ContractConflictException(ContractConflictException.TERMS_CHANGED_SINCE_CODE,
                    "The terms changed after this code was sent; start signing again.");
        }
        String otherHash = this.signedHashes.get(role == SignerRole.BUYER ? SignerRole.SELLER : SignerRole.BUYER);
        if (otherHash != null && !otherHash.equals(hash)) {
            throw new IllegalStateException("Contract " + this.contractId.value()
                    + " has a signature on other terms than the current ones");
        }
        //Mutate
        this.signedHashes.put(role, hash);
        if (this.signedHashes.size() == 2) {
            this.status = ContractStatus.SIGNED;
        }
        return new Signature(signatureId, this.contractId.value(), role, userId, verified.otpId(),
                verified.consentId(), authTime, signedAt, hash, ipAddress);
    }

    /**
     * WITHDRAW_OFFER (#233, milestone-escrow design §6.1): either party ends the offer before the second signature,
     * the party that already signed included (one signature binds no one). OFFERED/NEGOTIATING → WITHDRAWN,
     * terminal. No money moves: nothing is locked before SIGNED. Returns the party that withdrew; the caller writes
     * contract.withdrawn and contract.terminated(WITHDRAW_OFFER) in the same transaction, under the row lock that
     * also serialises it with a concurrent second signature.
     */
    public SignerRole withdraw(String userId) {
        //Guard
        SignerRole role = roleOf(userId).orElseThrow(() ->
                new UnauthorizedContractAccessException("This user is not a party of this contract."));
        if (this.coffeeTerms == null) {
            throw new ContractConflictException(ContractConflictException.LEGACY_TERMS,
                    "This contract has no coffee terms and cannot be withdrawn this way.");
        }
        if (this.status != ContractStatus.OFFERED && this.status != ContractStatus.NEGOTIATING) {
            throw new ContractConflictException(ContractConflictException.NOT_WITHDRAWABLE,
                    "Only an offer not yet signed by both parties can be withdrawn; this contract is "
                            + this.status + ".");
        }
        //Mutate
        this.status = ContractStatus.WITHDRAWN;
        return role;
    }

    /** Parties that have signed, BUYER and/or SELLER. */
    public Set<SignerRole> getSignedRoles() {
        return Set.copyOf(signedHashes.keySet());
    }

    /**
     * SIGNED → ACTIVE once every required deposit is locked (escrow.deposit_locked, #103, D40). Signing is not
     * activation: only this moves the contract to the money milestone. contract.activated is written by the
     * caller to the event outbox in the same transaction.
     */
    public void activate() {
        //Guard
        if (this.status != ContractStatus.SIGNED) {
            throw new ContractConflictException(ContractConflictException.NOT_ACTIVATABLE,
                    "Only a SIGNED contract can be activated, not " + this.status + ".");
        }
        //Mutate
        this.status = ContractStatus.ACTIVE;
    }

    /**
     * SIGNED → ACTIVATION_REFUND_PENDING (D42): the parties' cancel conditions hold. contract.activation_refund_requested
     * is written by the caller in the same transaction; escrow returns the locked deposits.
     */
    public void startActivationRefund() {
        if (this.status != ContractStatus.SIGNED) {
            throw new ContractConflictException(ContractConflictException.NOT_CANCELLABLE,
                    "Only a SIGNED contract waiting for its deposits can be cancelled, not " + this.status + ".");
        }
        this.status = ContractStatus.ACTIVATION_REFUND_PENDING;
    }

    /** ACTIVATION_REFUND_PENDING → ACTIVATION_FAILED once escrow confirmed every return (lock = 0). Terminal. */
    public void failActivation() {
        if (this.status != ContractStatus.ACTIVATION_REFUND_PENDING) {
            throw new ContractConflictException(ContractConflictException.NOT_CANCELLABLE,
                    "Activation can only fail after the refund started, not in " + this.status + ".");
        }
        this.status = ContractStatus.ACTIVATION_FAILED;
    }

    public List<DomainEvent> pullDomainEvents() {
        List<DomainEvent> events = new ArrayList<>(this.domainEvents);
        this.domainEvents.clear();
        return events;
    }
}
