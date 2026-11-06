package com.agricontract.product.application.usecase;

import com.agricontract.product.application.dto.ReleaseListingReservationRequest;
import com.agricontract.product.application.dto.ReservationResult;
import com.agricontract.product.application.dto.ReserveListingQuantityRequest;
import com.agricontract.product.common.exception.ListingNotFoundException;
import com.agricontract.product.common.exception.ReservationConflictException;
import com.agricontract.product.common.exception.ReservationNotFoundException;
import com.agricontract.product.domain.model.Listing;
import com.agricontract.product.domain.model.vo.*;
import com.agricontract.product.domain.repository.ListingRepository;
import com.agricontract.product.domain.repository.ReservationRepository;
import com.agricontract.product.infrastructure.persistence.mapper.ListingMapperImpl;
import com.agricontract.product.infrastructure.persistence.mapper.PlotRegistryMapperImpl;
import com.agricontract.product.testsupport.GoodsFixtures;
import com.agricontract.product.testsupport.PlotFixtures;
import com.agricontract.product.infrastructure.persistence.mapper.ReservationMapperImpl;
import com.agricontract.product.infrastructure.persistence.repository.ListingRepositoryImpl;
import com.agricontract.product.infrastructure.persistence.repository.ReservationRepositoryImpl;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

import static com.agricontract.product.common.exception.ReservationConflictException.*;
import static org.assertj.core.api.Assertions.*;

/**
 * Reserve + compensation release end to end on real MySQL (matrix 11r, 28): idempotent replay,
 * one winner on the last stock, frozen 409 codes, and release restoring quantity exactly once.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({ListingRepositoryImpl.class, ReservationRepositoryImpl.class,
        ListingMapperImpl.class, ReservationMapperImpl.class, PlotRegistryMapperImpl.class,
        ReserveListingQuantityUseCaseImpl.class, ReleaseListingReservationUseCaseImpl.class,
        ReservationReleaser.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Testcontainers(disabledWithoutDocker = true)
class ListingReservationUseCaseIntegrationTest {

    @Container
    @ServiceConnection
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0");

    private static final ListingId LISTING_ID = new ListingId("listing-1");
    private static final String COMPENSATION = "CREATE_PERSISTENCE_COMPENSATION";

    @Autowired private ReserveListingQuantityUseCase reserveUseCase;
    @Autowired private ReleaseListingReservationUseCase releaseUseCase;
    @Autowired private ListingRepository listingRepository;
    @Autowired private ReservationRepository reservationRepository;
    @Autowired private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        PlotFixtures.deletePlotsAndListings(jdbcTemplate);
        PlotFixtures.insertRegistryEntry(jdbcTemplate, PlotFixtures.PLOT_ID, "seller-1");
        listingRepository.save(Listing.create(LISTING_ID, "seller-1", new ProductId("product-1"),
                "Ca phe Robusta", "https://cdn.test/cover.jpg", new Quantity(new BigDecimal("100"), "kg"),
                new BigDecimal("10"), new Money(new BigDecimal("50000"), "VND"), LocalDate.of(2026, 12, 31),
                PlotFixtures.onePlot(), GoodsFixtures.coffeeGoods()));
    }

    // ---------- reserve ----------

    @Test
    void reserve_partialFill_thenInsufficient() {
        ReservationResult first = reserve("contract-1", "40");
        assertThat(first.created()).isTrue();
        assertThat(first.reservation().state()).isEqualTo(ReservationStatus.RESERVED);
        assertThat(first.reservation().quantityAvailable()).isEqualByComparingTo("60");

        assertThat(reserve("contract-2", "40").reservation().quantityAvailable()).isEqualByComparingTo("20");

        assertConflict(() -> reserve("contract-3", "30"), INSUFFICIENT_LISTING_QUANTITY);
        assertThat(available()).isEqualByComparingTo("20");
        assertThat(reservationRepository.findByContractId(new ContractId("contract-3"))).isEmpty();
    }

    @Test
    void reserve_replaySamePayload_returnsSameReservationWithoutSecondDecrement() {
        reserve("contract-1", "40");

        ReservationResult replay = reserve("contract-1", "40.000");

        assertThat(replay.created()).isFalse();
        assertThat(replay.reservation().contractId()).isEqualTo("contract-1");
        assertThat(replay.reservation().reservedQuantity()).isEqualByComparingTo("40");
        assertThat(available()).isEqualByComparingTo("60");
    }

    @Test
    void reserve_replayDifferentPayload_isStateConflict() {
        reserve("contract-1", "40");

        assertConflict(() -> reserve("contract-1", "41"), RESERVATION_STATE_CONFLICT);
        assertThat(available()).isEqualByComparingTo("60");
    }

    @Test
    void reserve_replayAfterRelease_isStateConflict() {
        reserve("contract-1", "40");
        release("contract-1", "40");

        assertConflict(() -> reserve("contract-1", "40"), RESERVATION_STATE_CONFLICT);
        assertThat(available()).isEqualByComparingTo("100");
    }

    @Test
    void reserve_concurrentForFinalStock_hasOneWinner() throws Exception {
        reserve("contract-0", "40");

        List<Object> outcomes = race(() -> reserve("contract-a", "60"), () -> reserve("contract-b", "60"));

        assertThat(outcomes).filteredOn(ReservationResult.class::isInstance).hasSize(1);
        assertThat(outcomes).filteredOn(ReservationConflictException.class::isInstance)
                .singleElement()
                .extracting(e -> ((ReservationConflictException) e).getCode())
                .isEqualTo(INSUFFICIENT_LISTING_QUANTITY);
        assertThat(available()).isEqualByComparingTo("0");
        assertThat(listingRepository.findById(LISTING_ID).orElseThrow().getStatus()).isEqualTo(ListingStatus.CLOSED);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM listing_reservations", Integer.class)).isEqualTo(2);
    }

    @Test
    void reserve_concurrentSameContractId_decrementsOnce() throws Exception {
        List<Object> outcomes = race(() -> reserve("contract-1", "40"), () -> reserve("contract-1", "40"));

        assertThat(outcomes).allMatch(ReservationResult.class::isInstance);
        assertThat(outcomes).extracting(o -> ((ReservationResult) o).created()).containsExactlyInAnyOrder(true, false);
        assertThat(available()).isEqualByComparingTo("60");
    }

    @Test
    void reserve_legacyNullListing_failsClosed() {
        jdbcTemplate.update("UPDATE listings SET quantity_available = NULL WHERE listing_id = ?", LISTING_ID.value());

        assertConflict(() -> reserve("contract-1", "40"), INSUFFICIENT_LISTING_QUANTITY);
        assertThat(reservationRepository.findByContractId(new ContractId("contract-1"))).isEmpty();
    }

    @Test
    void reserve_belowMinOrderQuantity_isInsufficient() {
        assertConflict(() -> reserve("contract-1", "9.999"), INSUFFICIENT_LISTING_QUANTITY);
        assertThat(available()).isEqualByComparingTo("100");
    }

    @Test
    void reserve_staleToken_isVersionMismatch() {
        assertConflict(() -> reserveUseCase.execute(new ReserveListingQuantityRequest(
                "contract-1", LISTING_ID.value(), new BigDecimal("40"), "2020-01-01T00:00:00Z")),
                LISTING_VERSION_MISMATCH);
        assertThat(available()).isEqualByComparingTo("100");
        assertThat(reservationRepository.findByContractId(new ContractId("contract-1"))).isEmpty();
    }

    @Test
    void reserve_closedListingWithStock_isListingStateConflict() {
        Listing listing = listingRepository.findById(LISTING_ID).orElseThrow();
        listing.close();
        listingRepository.save(listing);

        assertConflict(() -> reserve("contract-1", "40"), LISTING_STATE_CONFLICT);
    }

    @Test
    void reserve_unknownListing_isNotFound() {
        assertThatThrownBy(() -> reserveUseCase.execute(new ReserveListingQuantityRequest(
                "contract-1", "no-such-listing", new BigDecimal("40"), "2020-01-01T00:00:00Z")))
                .isInstanceOf(ListingNotFoundException.class);
    }

    // ---------- compensation release ----------

    @Test
    void release_restoresOnce_andReplayIsNoOp() {
        reserve("contract-1", "40");

        release("contract-1", "40");
        release("contract-1", "40");

        assertThat(available()).isEqualByComparingTo("100");
        assertThat(reservationRepository.findByContractId(new ContractId("contract-1")).orElseThrow().getStatus())
                .isEqualTo(ReservationStatus.RELEASED);
    }

    @Test
    void release_concurrentReplays_restoreOnce() throws Exception {
        reserve("contract-1", "40");

        List<Object> outcomes = race(() -> release("contract-1", "40"), () -> release("contract-1", "40"));

        assertThat(outcomes).noneMatch(Throwable.class::isInstance);
        assertThat(available()).isEqualByComparingTo("100");
    }

    @Test
    void release_reopensListingClosedByExhaustion() {
        reserve("contract-1", "100");
        assertThat(listingRepository.findById(LISTING_ID).orElseThrow().getStatus()).isEqualTo(ListingStatus.CLOSED);

        release("contract-1", "100");

        Listing listing = listingRepository.findById(LISTING_ID).orElseThrow();
        assertThat(listing.getStatus()).isEqualTo(ListingStatus.ACTIVE);
        assertThat(listing.getQuantityAvailable()).isEqualByComparingTo("100");
    }

    @Test
    void release_keepsOtherwiseClosedListingClosed() {
        reserve("contract-1", "40");
        Listing listing = listingRepository.findById(LISTING_ID).orElseThrow();
        listing.close();
        listingRepository.save(listing);

        release("contract-1", "40");

        Listing after = listingRepository.findById(LISTING_ID).orElseThrow();
        assertThat(after.getStatus()).isEqualTo(ListingStatus.CLOSED);
        assertThat(after.getQuantityAvailable()).isEqualByComparingTo("100");
    }

    @Test
    void release_payloadMismatch_isStateConflict() {
        reserve("contract-1", "40");

        assertConflict(() -> release("contract-1", "39"), RESERVATION_STATE_CONFLICT);
        assertThat(available()).isEqualByComparingTo("60");
    }

    @Test
    void release_afterSignedOrCommitted_isStateConflict() {
        reserve("contract-1", "40");
        reservationRepository.transition(new ContractId("contract-1"),
                ReservationStatus.RESERVED, ReservationStatus.SIGNED_RELEASABLE);
        assertConflict(() -> release("contract-1", "40"), RESERVATION_STATE_CONFLICT);

        reservationRepository.transition(new ContractId("contract-1"),
                ReservationStatus.SIGNED_RELEASABLE, ReservationStatus.COMMITTED);
        assertConflict(() -> release("contract-1", "40"), RESERVATION_STATE_CONFLICT);
        assertThat(available()).isEqualByComparingTo("60");
    }

    @Test
    void release_unknownReservation_isNotFound() {
        assertThatThrownBy(() -> release("contract-x", "40")).isInstanceOf(ReservationNotFoundException.class);
    }

    // ---------- helpers ----------

    private ReservationResult reserve(String contractId, String quantity) {
        String token = listingRepository.findById(LISTING_ID).orElseThrow().getVersionToken();
        return reserveUseCase.execute(new ReserveListingQuantityRequest(
                contractId, LISTING_ID.value(), new BigDecimal(quantity), token));
    }

    private Void release(String contractId, String quantity) {
        releaseUseCase.execute(contractId,
                new ReleaseListingReservationRequest(LISTING_ID.value(), new BigDecimal(quantity), COMPENSATION));
        return null;
    }

    private static void assertConflict(ThrowingCallable call, String code) {
        assertThatThrownBy(call)
                .isInstanceOf(ReservationConflictException.class)
                .extracting(e -> ((ReservationConflictException) e).getCode())
                .isEqualTo(code);
    }

    // Starts both calls together; each outcome is the call's result or the exception it threw.
    private static List<Object> race(Callable<?> a, Callable<?> b) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Object>> futures = new ArrayList<>();
            for (Callable<?> call : List.of(a, b)) {
                futures.add(pool.submit(() -> {
                    start.await();
                    try {
                        return call.call();
                    } catch (Exception e) {
                        return e;
                    }
                }));
            }
            start.countDown();
            List<Object> outcomes = new ArrayList<>();
            for (Future<Object> future : futures) {
                outcomes.add(future.get(20, TimeUnit.SECONDS));
            }
            return outcomes;
        } finally {
            pool.shutdownNow();
        }
    }

    private BigDecimal available() {
        return jdbcTemplate.queryForObject("SELECT quantity_available FROM listings WHERE listing_id = ?",
                BigDecimal.class, LISTING_ID.value());
    }
}
