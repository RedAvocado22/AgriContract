package com.agricontract.contract.application.usecase;

import static com.agricontract.contract.domain.model.terms.CoffeeTermsFixtures.RULES;
import static com.agricontract.contract.domain.model.terms.CoffeeTermsFixtures.goods;
import static com.agricontract.contract.domain.model.terms.CoffeeTermsFixtures.input;
import static com.agricontract.contract.domain.model.terms.CoffeeTermsFixtures.template;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.agricontract.contract.application.dto.CreateContractCommand;
import com.agricontract.contract.application.dto.ListingReservation;
import com.agricontract.contract.application.dto.ListingResponse;
import com.agricontract.contract.application.dto.UserInfo;
import com.agricontract.contract.application.exception.IdempotencyKeyReusedException;
import com.agricontract.contract.application.exception.ListingServiceUnavailableException;
import com.agricontract.contract.application.exception.UnauthorizedContractActionException;
import com.agricontract.contract.application.exception.UserServiceUnavailableException;
import com.agricontract.contract.application.port.ContractEventPublisher;
import com.agricontract.contract.application.port.IdempotencyStore;
import com.agricontract.contract.application.port.ListingPort;
import com.agricontract.contract.application.port.UserPort;
import com.agricontract.contract.application.service.ReservationCompensation;
import com.agricontract.contract.domain.exception.ContractConflictException;
import com.agricontract.contract.domain.model.Contract;
import com.agricontract.contract.domain.model.terms.CoffeeGoodsTerms;
import com.agricontract.contract.domain.model.terms.ContractTermsInput;
import com.agricontract.contract.domain.model.terms.InvalidContractTermsException;
import com.agricontract.contract.domain.model.terms.MilestoneTerm;
import com.agricontract.contract.domain.model.vo.ContractId;
import com.agricontract.contract.domain.model.vo.ContractStatus;
import com.agricontract.contract.domain.repository.ContractRepository;
import com.agricontract.contract.infrastructure.terms.JcsTermsCanonicalizer;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

/** #232 create ordering: validate → read → reserve → local transaction, with compensation after the reserve. */
class CreateContractUseCaseTest {
    private static final String BUYER = "0b7e3b7a-1c1e-4c5e-9f0a-3d2b1a0c9e8f";
    private static final String SELLER = "5a4c3b2a-1f0e-4d9c-8b7a-6f5e4d3c2b1a";
    private static final String LISTING = goods().listingId().toString();
    private static final String TOKEN = "2026-11-10T06:00:00.123456Z";
    private static final BigDecimal TOTAL = new BigDecimal("2500.5");
    private static final Instant NOW = Instant.parse("2026-11-10T07:00:00Z");

    private final ContractRepository contracts = mock(ContractRepository.class);
    private final IdempotencyStore idempotency = mock(IdempotencyStore.class);
    private final ListingPort listings = mock(ListingPort.class);
    private final UserPort users = mock(UserPort.class);
    private final ContractEventPublisher events = mock(ContractEventPublisher.class);
    private final TransactionTemplate tx = mock(TransactionTemplate.class);
    private CreateContractUseCase useCase;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        when(tx.execute(any())).thenAnswer(call -> ((TransactionCallback<Object>) call.getArgument(0))
                .doInTransaction(null));
        useCase = new CreateContractUseCase(contracts, idempotency, listings, users,
                new ReservationCompensation(listings, 3, 0), events, new JcsTermsCanonicalizer(), RULES,
                template(), tx, Clock.fixed(NOW, ZoneOffset.UTC));

        when(idempotency.lookup(eq(BUYER), anyString(), any(), anyString())).thenReturn(Optional.empty());
        when(idempotency.claim(eq(BUYER), anyString(), any(), anyString()))
                .thenReturn(IdempotencyStore.Outcome.FIRST);
        when(listings.getListing(LISTING)).thenReturn(listing(SELLER));
        when(users.findUser(BUYER)).thenReturn(Optional.of(user(BUYER, "buyer@example.com")));
        when(users.findUser(SELLER)).thenReturn(Optional.of(user(SELLER, "seller@example.com")));
        when(listings.contractSnapshot(LISTING, TOKEN)).thenReturn(goods());
        when(listings.reserve(anyString(), eq(LISTING), any(), eq(TOKEN))).thenAnswer(call -> new ListingReservation(
                call.getArgument(0), LISTING, call.getArgument(2), "RESERVED", new BigDecimal("7499.5")));
    }

    @Test
    void reservesTheScheduleTotal_beforeStoringTheOffer_andPublishesContractCreatedInTheSameTransaction() {
        CreateContractUseCase.Result result = useCase.execute(command(input()));

        ArgumentCaptor<String> reservedId = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Contract> stored = ArgumentCaptor.forClass(Contract.class);
        InOrder order = inOrder(listings, idempotency, contracts, events);
        order.verify(listings).reserve(reservedId.capture(), eq(LISTING), eq(TOTAL), eq(TOKEN));
        order.verify(idempotency).claim(eq(BUYER), eq("CREATE_CONTRACT"), any(), anyString());
        order.verify(contracts).save(stored.capture());
        order.verify(events).contractCreated(stored.capture(), eq(NOW));
        order.verify(idempotency).recordResult(eq(BUYER), eq("CREATE_CONTRACT"), any(), eq(reservedId.getValue()));

        Contract contract = stored.getValue();
        assertThat(contract.getContractId().value()).isEqualTo(reservedId.getValue());
        assertThat(contract.getStatus()).isEqualTo(ContractStatus.OFFERED);
        assertThat(contract.getStoredReservedQuantity()).isEqualByComparingTo(TOTAL);
        assertThat(contract.getCoffeeTerms().snapshot().goodsTerms()).isEqualTo(goods());
        assertThat(contract.getBuyerId()).isEqualTo(BUYER);
        assertThat(contract.getSellerId()).isEqualTo(SELLER);
        assertThat(result.replayed()).isFalse();
        assertThat(result.contract().reservedQuantity()).isEqualByComparingTo(TOTAL);
        verify(listings, never()).releaseReservation(anyString(), anyString(), any());
    }

    @Test
    void insufficientQuantity_isA409_withNothingStoredAndNothingToRelease() {
        when(listings.reserve(anyString(), eq(LISTING), any(), eq(TOKEN))).thenThrow(
                new ContractConflictException("INSUFFICIENT_LISTING_QUANTITY", "Only 100 kg left"));

        assertThatThrownBy(() -> useCase.execute(command(input())))
                .isInstanceOf(ContractConflictException.class)
                .extracting(e -> ((ContractConflictException) e).getCode())
                .isEqualTo("INSUFFICIENT_LISTING_QUANTITY");
        verify(contracts, never()).save(any());
        verifyNoInteractions(events);
        verify(listings, never()).releaseReservation(anyString(), anyString(), any());
    }

    @Test
    void reserveWithoutAnAnswer_failsClosed_andGivesBackWhateverMayHaveBeenReserved() {
        when(listings.reserve(anyString(), eq(LISTING), any(), eq(TOKEN))).thenThrow(
                new ListingServiceUnavailableException("timeout", null));

        assertThatThrownBy(() -> useCase.execute(command(input())))
                .isInstanceOf(ListingServiceUnavailableException.class);
        ArgumentCaptor<String> reservedId = ArgumentCaptor.forClass(String.class);
        verify(listings).reserve(reservedId.capture(), eq(LISTING), eq(TOTAL), eq(TOKEN));
        verify(listings).releaseReservation(reservedId.getValue(), LISTING, TOTAL);
        verify(contracts, never()).save(any());
        verifyNoInteractions(events);
    }

    @Test
    void snapshotUnavailable_failsClosed_beforeAnyReservation() {
        when(listings.contractSnapshot(LISTING, TOKEN)).thenThrow(
                new ListingServiceUnavailableException("down", null));

        assertThatThrownBy(() -> useCase.execute(command(input())))
                .isInstanceOf(ListingServiceUnavailableException.class);
        verify(listings, never()).reserve(anyString(), anyString(), any(), anyString());
        verify(contracts, never()).save(any());
    }

    @Test
    void userServiceUnavailable_failsClosed_beforeAnyReservation() {
        when(users.findUser(SELLER)).thenThrow(new UserServiceUnavailableException("down", null));

        assertThatThrownBy(() -> useCase.execute(command(input())))
                .isInstanceOf(UserServiceUnavailableException.class);
        verify(listings, never()).reserve(anyString(), anyString(), any(), anyString());
    }

    @Test
    void localPersistenceFailure_releasesTheReservation_andLeavesNoOffer() {
        when(contracts.save(any())).thenThrow(new DataIntegrityViolationException("boom"));

        assertThatThrownBy(() -> useCase.execute(command(input())))
                .isInstanceOf(DataIntegrityViolationException.class);
        ArgumentCaptor<String> reservedId = ArgumentCaptor.forClass(String.class);
        verify(listings).reserve(reservedId.capture(), eq(LISTING), eq(TOTAL), eq(TOKEN));
        verify(listings).releaseReservation(reservedId.getValue(), LISTING, TOTAL);
    }

    @Test
    void outboxFailure_rollsBackAndReleases() {
        doThrow(new IllegalStateException("outbox down")).when(events).contractCreated(any(), any());

        assertThatThrownBy(() -> useCase.execute(command(input())))
                .isInstanceOf(IllegalStateException.class);
        verify(listings).releaseReservation(anyString(), eq(LISTING), eq(TOTAL));
    }

    @Test
    void releaseKeepsBeingRetried_untilProductServiceAnswers() {
        when(contracts.save(any())).thenThrow(new DataIntegrityViolationException("boom"));
        doThrow(new ListingServiceUnavailableException("down", null))
                .doThrow(new ListingServiceUnavailableException("down", null))
                .doNothing()
                .when(listings).releaseReservation(anyString(), anyString(), any());

        assertThatThrownBy(() -> useCase.execute(command(input())))
                .isInstanceOf(DataIntegrityViolationException.class);
        verify(listings, org.mockito.Mockito.times(3)).releaseReservation(anyString(), eq(LISTING), eq(TOTAL));
    }

    @Test
    void reservationThatDoesNotMatchTheRequest_isReleased_andNotStored() {
        when(listings.reserve(anyString(), eq(LISTING), any(), eq(TOKEN))).thenAnswer(call -> new ListingReservation(
                call.getArgument(0), LISTING, new BigDecimal("1000"), "RESERVED", BigDecimal.ZERO));

        assertThatThrownBy(() -> useCase.execute(command(input())))
                .isInstanceOf(IllegalStateException.class);
        verify(contracts, never()).save(any());
        verify(listings).releaseReservation(anyString(), eq(LISTING), eq(TOTAL));
    }

    @Test
    void nonCoffeeGoods_areRejected_beforeAnyReservation() {
        CoffeeGoodsTerms g = goods();
        when(listings.contractSnapshot(LISTING, TOKEN)).thenReturn(new CoffeeGoodsTerms("RICE", g.listingId(),
                g.productId(), g.categoryId(), g.categoryName(), g.productName(), g.unitOfMeasure(), g.cropYear(),
                g.harvestSeason(), g.originSnapshot(), null));

        assertThatThrownBy(() -> useCase.execute(command(input())))
                .isInstanceOf(InvalidContractTermsException.class)
                .hasMessageContaining("COFFEE");
        verify(listings, never()).reserve(anyString(), anyString(), any(), anyString());
    }

    @Test
    void nonCoffeeQualityTerms_areRejected_beforeAnyRemoteCall() {
        ContractTermsInput t = input();
        var q = t.qualityTerms();
        ContractTermsInput rice = withQuality(t, new com.agricontract.contract.domain.model.terms.CoffeeQualityTerms(
                "RICE", q.committedSpec(), q.deviationPolicy()));

        assertThatThrownBy(() -> useCase.execute(command(rice)))
                .isInstanceOf(InvalidContractTermsException.class);
        verifyNoInteractions(listings);
    }

    @Test
    void moreThanThreeDecimalsOfKg_cannotBeReservedExactly_andIsRejected() {
        ContractTermsInput t = input();
        ContractTermsInput fine = withSchedule(t, List.of(
                new MilestoneTerm(1, new BigDecimal("1000.0005"), LocalDate.of(2026, 12, 15), 2)));

        assertThatThrownBy(() -> useCase.execute(command(fine)))
                .isInstanceOf(InvalidContractTermsException.class);
        verifyNoInteractions(listings);
    }

    @Test
    void sellerCannotOfferOnTheirOwnListing() {
        when(listings.getListing(LISTING)).thenReturn(listing(BUYER));

        assertThatThrownBy(() -> useCase.execute(command(input())))
                .isInstanceOf(UnauthorizedContractActionException.class);
        verify(listings, never()).reserve(anyString(), anyString(), any(), anyString());
    }

    @Test
    void listingChangedSinceTheBuyerLoadedIt_isA409_beforeAnySnapshotOrReservation() {
        ListingResponse current = listing(SELLER);
        when(listings.getListing(LISTING)).thenReturn(new ListingResponse(current.listingId(), current.sellerId(),
                current.productId(), current.productName(), current.quantity(), current.quantityUnit(),
                current.priceFloor(), current.currency(), current.deliveryDeadline(), current.status(),
                "2026-11-10T07:00:00.000001Z"));

        assertThatThrownBy(() -> useCase.execute(command(input())))
                .isInstanceOfSatisfying(ContractConflictException.class,
                        e -> assertThat(e.getCode()).isEqualTo("LISTING_VERSION_MISMATCH"));
        verify(listings, never()).contractSnapshot(anyString(), anyString());
        verify(listings, never()).reserve(anyString(), anyString(), any(), anyString());
        verify(contracts, never()).save(any());
    }

    @Test
    void retryWithTheSameKey_returnsTheFirstContract_withoutReservingAgain() {
        String existingId = UUID.randomUUID().toString();
        when(idempotency.lookup(eq(BUYER), anyString(), any(), anyString()))
                .thenReturn(Optional.of(IdempotencyStore.Outcome.REPLAY));
        when(idempotency.resultOf(eq(BUYER), eq("CREATE_CONTRACT"), any())).thenReturn(Optional.of(existingId));
        when(contracts.findById(new ContractId(existingId))).thenReturn(Optional.of(offered(existingId)));

        CreateContractUseCase.Result result = useCase.execute(command(input()));

        assertThat(result.replayed()).isTrue();
        assertThat(result.contract().contractId()).isEqualTo(existingId);
        verify(listings, never()).reserve(anyString(), anyString(), any(), anyString());
    }

    @Test
    void sameKeyForADifferentRequest_isRefused_withoutReserving() {
        when(idempotency.lookup(eq(BUYER), anyString(), any(), anyString()))
                .thenReturn(Optional.of(IdempotencyStore.Outcome.CONFLICT));

        assertThatThrownBy(() -> useCase.execute(command(input())))
                .isInstanceOf(IdempotencyKeyReusedException.class);
        verifyNoInteractions(listings);
    }

    @Test
    void concurrentRequestWithTheSameKeyWins_thisReservationIsReleased_andTheWinnerReturned() {
        String winnerId = UUID.randomUUID().toString();
        when(idempotency.claim(eq(BUYER), anyString(), any(), anyString()))
                .thenReturn(IdempotencyStore.Outcome.REPLAY);
        when(idempotency.resultOf(eq(BUYER), eq("CREATE_CONTRACT"), any())).thenReturn(Optional.of(winnerId));
        when(contracts.findById(new ContractId(winnerId))).thenReturn(Optional.of(offered(winnerId)));

        CreateContractUseCase.Result result = useCase.execute(command(input()));

        assertThat(result.replayed()).isTrue();
        assertThat(result.contract().contractId()).isEqualTo(winnerId);
        ArgumentCaptor<String> reservedId = ArgumentCaptor.forClass(String.class);
        verify(listings).reserve(reservedId.capture(), eq(LISTING), eq(TOTAL), eq(TOKEN));
        verify(listings).releaseReservation(reservedId.getValue(), LISTING, TOTAL);
        verify(contracts, never()).save(any());
    }

    private static CreateContractCommand command(ContractTermsInput terms) {
        return new CreateContractCommand(BUYER, UUID.fromString("9d0c7c5e-6f39-4b8e-9a43-0c7a6a1d2f11"), LISTING,
                TOKEN, terms);
    }

    private Contract offered(String id) {
        return Contract.offerCoffee(new ContractId(id), LISTING, BUYER, SELLER, "HTX Mua", "HTX Bán",
                "buyer@example.com", "seller@example.com",
                com.agricontract.contract.domain.model.terms.CanonicalTerms.of(
                        com.agricontract.contract.domain.model.terms.CoffeeTermsFixtures.snapshot(),
                        new JcsTermsCanonicalizer()));
    }

    private static ListingResponse listing(String sellerId) {
        return new ListingResponse(LISTING, sellerId, goods().productId().toString(), "Robusta", new BigDecimal("10000"),
                "KG", new BigDecimal("90000"), "VND", LocalDate.of(2027, 2, 1), "AVAILABLE", TOKEN);
    }

    private static UserInfo user(String id, String email) {
        return new UserInfo(id, "HTX " + id.substring(0, 4), email, "BUYER", "VERIFIED", null, null);
    }

    private static ContractTermsInput withQuality(ContractTermsInput t,
                                                  com.agricontract.contract.domain.model.terms.CoffeeQualityTerms q) {
        return new ContractTermsInput(t.agreedPrice(), t.currency(), t.milestoneSchedule(), q, t.deliveryTerms(),
                t.inspectionTerms(), t.priceBand(), t.priceAdjustmentRule(), t.toleranceRate(),
                t.shortfallPenaltyThreshold(), t.buyerPenaltyRate(), t.sellerPenaltyRate(),
                t.forceMajeureReportWindowDays(), t.buyerConfirmWindowDays(), t.buyerDepositRate(),
                t.disputeFloorReleaseRate(), t.sellerDepositRate(), t.level2InspectorOrg(), t.legalProfile());
    }

    private static ContractTermsInput withSchedule(ContractTermsInput t, List<MilestoneTerm> schedule) {
        return new ContractTermsInput(t.agreedPrice(), t.currency(), schedule, t.qualityTerms(), t.deliveryTerms(),
                t.inspectionTerms(), t.priceBand(), t.priceAdjustmentRule(), t.toleranceRate(),
                t.shortfallPenaltyThreshold(), t.buyerPenaltyRate(), t.sellerPenaltyRate(),
                t.forceMajeureReportWindowDays(), t.buyerConfirmWindowDays(), t.buyerDepositRate(),
                t.disputeFloorReleaseRate(), t.sellerDepositRate(), t.level2InspectorOrg(), t.legalProfile());
    }
}
