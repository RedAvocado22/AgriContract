package com.agricontract.product.application.usecase;

import com.agricontract.product.application.dto.CoffeeQualitySpecDto;
import com.agricontract.product.application.dto.CreateListingRequest;
import com.agricontract.product.application.dto.ListingResponse;
import com.agricontract.product.application.dto.ReleaseListingReservationRequest;
import com.agricontract.product.application.dto.ReserveListingQuantityRequest;
import com.agricontract.product.application.dto.UserEligibility;
import com.agricontract.product.application.port.UserEligibilityPort;
import com.agricontract.product.common.exception.IdempotencyKeyReusedException;
import com.agricontract.product.common.exception.NotListingSellerException;
import com.agricontract.product.common.exception.OfferedQuantityBelowReservedException;
import com.agricontract.product.common.exception.PlotNotEligibleException;
import com.agricontract.product.common.exception.PlotNotOwnedException;
import com.agricontract.product.common.exception.ReservationConflictException;
import com.agricontract.product.domain.model.Listing;
import com.agricontract.product.domain.model.vo.*;
import com.agricontract.product.domain.repository.ListingRepository;
import com.agricontract.product.domain.repository.ReservationRepository;
import com.agricontract.product.infrastructure.config.ClockConfig;
import com.agricontract.product.infrastructure.persistence.SpringTransactionRunner;
import com.agricontract.product.infrastructure.persistence.mapper.ListingMapperImpl;
import com.agricontract.product.infrastructure.persistence.mapper.PlotRegistryMapperImpl;
import com.agricontract.product.infrastructure.persistence.mapper.ReservationMapperImpl;
import com.agricontract.product.infrastructure.persistence.repository.ListingEditKeyRepositoryImpl;
import com.agricontract.product.infrastructure.persistence.repository.ListingRepositoryImpl;
import com.agricontract.product.infrastructure.persistence.repository.PlotRegistryRepositoryImpl;
import com.agricontract.product.infrastructure.persistence.repository.ReservationRepositoryImpl;
import com.agricontract.product.testsupport.GoodsFixtures;
import com.agricontract.product.testsupport.PlotFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
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
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

/**
 * updateListing on real MySQL (#433, D48, design §4/§8b.1): quantity rule against live reservations, status
 * recompute, version token, Idempotency-Key replay, and the edit racing reserve/release.
 * Not transactional per test so concurrent callers really race on committed rows.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({ListingRepositoryImpl.class, ReservationRepositoryImpl.class, PlotRegistryRepositoryImpl.class,
        ListingEditKeyRepositoryImpl.class, ListingMapperImpl.class, ReservationMapperImpl.class,
        PlotRegistryMapperImpl.class, SpringTransactionRunner.class, ClockConfig.class,
        UpdateListingUseCaseImpl.class, ListingPlotPicker.class, SellerTradingGate.class,
        ReserveListingQuantityUseCaseImpl.class, ReleaseListingReservationUseCaseImpl.class,
        ReservationReleaser.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Testcontainers(disabledWithoutDocker = true)
class UpdateListingIntegrationTest {

    @Container
    @ServiceConnection
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0");

    private static final String SELLER = "seller-1";
    private static final ListingId LISTING_ID = new ListingId("listing-1");
    private static final String PLOT_2 = "plot-2";
    private static final CoffeeQualitySpecDto SPEC = new CoffeeQualitySpecDto(Commodity.COFFEE, CoffeeType.ROBUSTA,
            new BigDecimal("12.5"), new BigDecimal("0.5"), new BigDecimal("5.0"));

    @Autowired private UpdateListingUseCase updateUseCase;
    @Autowired private ReserveListingQuantityUseCase reserveUseCase;
    @Autowired private ReleaseListingReservationUseCase releaseUseCase;
    @Autowired private ListingRepository listingRepository;
    @Autowired private JdbcTemplate jdbcTemplate;
    @MockBean private UserEligibilityPort userEligibilityPort;
    @SpyBean private ReservationRepository reservationRepository;

    private final AtomicInteger keys = new AtomicInteger();

    @BeforeEach
    void setUp() {
        PlotFixtures.deletePlotsAndListings(jdbcTemplate);
        PlotFixtures.insertRegistryEntry(jdbcTemplate, PlotFixtures.PLOT_ID, SELLER);
        PlotFixtures.insertRegistryEntry(jdbcTemplate, PLOT_2, SELLER);
        PlotFixtures.insertRegistryEntry(jdbcTemplate, "plot-other", "seller-2");
        // 50 t offered, at most 1 t per contract minimum
        listingRepository.save(Listing.create(LISTING_ID, SELLER, new ProductId("product-1"),
                "Ca phe Robusta", "https://cdn.test/cover.jpg", new BigDecimal("50000"), new BigDecimal("1000"),
                new BigDecimal("50000.00"), PlotFixtures.onePlot(), GoodsFixtures.coffeeGoods()));
        when(userEligibilityPort.load(anyString())).thenAnswer(inv -> Optional.of(
                new UserEligibility(inv.getArgument(0), "VERIFIED", null, null)));
    }

    private static CreateListingRequest request(String offered, String... plots) {
        return new CreateListingRequest("product-1", "Ca phe Robusta", "KG", new BigDecimal(offered),
                new BigDecimal("1000"), "52000.00", List.of(plots.length == 0 ? new String[]{PlotFixtures.PLOT_ID} : plots),
                "Buon Ma Thuot, Dak Lak", SPEC, "Bao day 60 kg", "Kho HTX Ea Kar", LocalDate.of(2026, 12, 1), null);
    }

    private ListingResponse edit(String offered, String... plots) {
        return updateUseCase.execute(SELLER, LISTING_ID.value(), "edit-" + keys.incrementAndGet(),
                request(offered, plots));
    }

    private void reserve(String contractId, String quantity) {
        reserveUseCase.execute(new ReserveListingQuantityRequest(contractId, LISTING_ID.value(),
                new BigDecimal(quantity), token()));
    }

    private void release(String contractId, String quantity) {
        releaseUseCase.execute(contractId, new ReleaseListingReservationRequest(LISTING_ID.value(),
                new BigDecimal(quantity), "CREATE_PERSISTENCE_COMPENSATION"));
    }

    private String token() {
        return listingRepository.findById(LISTING_ID).orElseThrow().getVersionToken();
    }

    private Map<String, Object> row() {
        return jdbcTemplate.queryForMap("""
                SELECT quantity, quantity_available, status, close_reason, price_floor FROM listings
                WHERE listing_id = ?""", LISTING_ID.value());
    }

    @Test
    void raisingTheOffer_keepsReservedOut_D48Example() {
        reserve("contract-1", "20000");
        String before = token();

        ListingResponse edited = edit("60000");

        assertThat(edited.offeredQuantity()).isEqualByComparingTo("60000");
        assertThat(edited.quantityAvailable()).isEqualByComparingTo("40000");
        assertThat(edited.status()).isEqualTo(ListingStatus.PARTIALLY_COMMITTED);
        assertThat(edited.askingUnitPrice()).isEqualTo("52000.00");
        assertThat(edited.listingVersionToken()).isNotEqualTo(before).isEqualTo(token());
        assertThat(row()).containsEntry("status", "PARTIALLY_COMMITTED");
    }

    @Test
    void loweringToTheReservedTotal_closes_andAReleaseDoesNotReopen_untilRepublished() {
        reserve("contract-1", "20000");

        ListingResponse closed = edit("20000");
        assertThat(closed.quantityAvailable()).isEqualByComparingTo("0");
        assertThat(closed.status()).isEqualTo(ListingStatus.CLOSED);
        assertThat(row()).containsEntry("close_reason", "SELLER_CLOSED");

        release("contract-1", "20000");
        assertThat(row()).containsEntry("status", "CLOSED").containsEntry("close_reason", "SELLER_CLOSED");
        assertThat((BigDecimal) row().get("quantity_available")).isEqualByComparingTo("20000");

        ListingResponse republished = edit("20000");
        assertThat(republished.status()).isEqualTo(ListingStatus.AVAILABLE);
        assertThat(republished.quantityAvailable()).isEqualByComparingTo("20000");
        assertThat(row().get("close_reason")).isNull();
    }

    @Test
    void belowTheReservedTotal_is409_andNothingChanges() {
        reserve("contract-1", "20000");
        String before = token();

        assertThatThrownBy(() -> edit("19999.999"))
                .isInstanceOf(OfferedQuantityBelowReservedException.class)
                .extracting(e -> ((OfferedQuantityBelowReservedException) e).getCode())
                .isEqualTo("OFFERED_QUANTITY_BELOW_RESERVED");

        assertThat((BigDecimal) row().get("quantity")).isEqualByComparingTo("50000");
        assertThat((BigDecimal) row().get("quantity_available")).isEqualByComparingTo("30000");
        assertThat(token()).isEqualTo(before);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM listing_edit_keys", Integer.class)).isZero();
    }

    @Test
    void releasedReservations_doNotCountAsReserved() {
        reserve("contract-1", "20000");
        release("contract-1", "20000");

        ListingResponse edited = edit("10000");

        assertThat(edited.quantityAvailable()).isEqualByComparingTo("10000");
        assertThat(edited.status()).isEqualTo(ListingStatus.AVAILABLE);
    }

    @Test
    void anotherSeller_is403_andNothingChanges() {
        assertThatThrownBy(() -> updateUseCase.execute("seller-2", LISTING_ID.value(), "k", request("60000")))
                .isInstanceOf(NotListingSellerException.class);

        assertThat((BigDecimal) row().get("quantity")).isEqualByComparingTo("50000");
    }

    @Test
    void legacyNullCounterWithoutReservation_isInitializedFromTheOffer() {
        jdbcTemplate.update("UPDATE listings SET quantity_available = NULL WHERE listing_id = ?", LISTING_ID.value());

        ListingResponse edited = edit("45000");

        assertThat(edited.quantityAvailable()).isEqualByComparingTo("45000");
        assertThat(edited.status()).isEqualTo(ListingStatus.AVAILABLE);
    }

    @Test
    void legacyListingWithoutQualitySpec_is400_andStaysReadable() {
        jdbcTemplate.update("""
                INSERT INTO listings (listing_id, seller_id, product_id, product_name, cover_image_url, quantity,
                    quantity_unit, price_floor, currency, status, created_at, updated_at)
                VALUES ('legacy-1', ?, 'product-1', 'Ca phe Robusta', '', 500, 'kg', 10000, 'VND',
                    'AVAILABLE', UTC_TIMESTAMP(), UTC_TIMESTAMP(6))""", SELLER);

        assertThatThrownBy(() -> updateUseCase.execute(SELLER, "legacy-1", "k", request("500")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("legacy");

        Listing legacy = listingRepository.findById(new ListingId("legacy-1")).orElseThrow();
        assertThat(legacy.isLegacyQualitySpecMissing()).isTrue();
        assertThat(legacy.getOfferedQuantity()).isEqualByComparingTo("500");
    }

    @Test
    void plotStillPending_is400_andAnotherSellersPlotIsRefused() {
        jdbcTemplate.update("UPDATE plot_registry_entry SET geo_verification_status = 'PENDING' WHERE registry_entry_id = ?",
                PLOT_2);

        assertThatThrownBy(() -> edit("50000", PlotFixtures.PLOT_ID, PLOT_2))
                .isInstanceOf(PlotNotEligibleException.class);
        assertThatThrownBy(() -> edit("50000", "plot-other"))
                .isInstanceOf(PlotNotOwnedException.class);
    }

    @Test
    void productIdCannotChange() {
        CreateListingRequest other = new CreateListingRequest("product-2", "Ca phe Robusta", "KG",
                new BigDecimal("50000"), new BigDecimal("1000"), "52000.00", List.of(PlotFixtures.PLOT_ID),
                "Buon Ma Thuot, Dak Lak", SPEC, null, null, null, null);

        assertThatThrownBy(() -> updateUseCase.execute(SELLER, LISTING_ID.value(), "k", other))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("productId");
    }

    @Test
    void tickingAndUntickingPlots_replacesTheCopies() {
        ListingResponse edited = edit("50000", PLOT_2);

        assertThat(edited.registryEntryIds()).containsExactly(PLOT_2);
        assertThat(jdbcTemplate.queryForList("SELECT source_registry_entry_id FROM listing_plot WHERE listing_id = ?",
                String.class, LISTING_ID.value())).containsExactly(PLOT_2);

        assertThat(edit("50000", PlotFixtures.PLOT_ID, PLOT_2).registryEntryIds())
                .containsExactlyInAnyOrder(PlotFixtures.PLOT_ID, PLOT_2);
    }

    @Test
    void editChangesTheVersionToken_soABuyerHoldingTheOldOneMustReload() {
        String old = token();

        edit("50000");

        assertThatThrownBy(() -> reserveUseCase.execute(new ReserveListingQuantityRequest("contract-1",
                LISTING_ID.value(), new BigDecimal("1000"), old)))
                .isInstanceOf(ReservationConflictException.class)
                .extracting(e -> ((ReservationConflictException) e).getCode())
                .isEqualTo(ReservationConflictException.LISTING_VERSION_MISMATCH);
    }

    @Test
    void replayWithTheSameKey_returnsTheListing_withoutASecondChange() {
        ListingResponse first = updateUseCase.execute(SELLER, LISTING_ID.value(), "edit-k", request("60000"));
        reserve("contract-1", "5000"); // inventory moved since: a second apply would recompute 60 - 5 = 55 t

        ListingResponse replay = updateUseCase.execute(SELLER, LISTING_ID.value(), "edit-k", request("60000.000"));

        assertThat(replay.listingVersionToken()).isEqualTo(first.listingVersionToken());
        assertThat(replay.quantityAvailable()).isEqualByComparingTo("55000");
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM listing_edit_keys", Integer.class)).isEqualTo(1);

        assertThatThrownBy(() -> updateUseCase.execute(SELLER, LISTING_ID.value(), "edit-k", request("61000")))
                .isInstanceOf(IdempotencyKeyReusedException.class);
        assertThat((BigDecimal) row().get("quantity")).isEqualByComparingTo("60000");
    }

    /**
     * Runs {@code concurrent} in the window between the edit counting reservations and writing the row, and gives it
     * 2 s to commit. With the row lock it cannot commit before the edit does (its listing update waits), so the edit
     * is never computed from a reserved total that is already stale.
     */
    private void duringTheEdit(Callable<?> concurrent, Runnable edit) throws Exception {
        ExecutorService pool = Executors.newSingleThreadExecutor();
        List<Future<?>> started = new CopyOnWriteArrayList<>();
        doAnswer(inv -> {
            Object reserved = inv.callRealMethod();
            Future<?> other = pool.submit(concurrent);
            started.add(other);
            try {
                other.get(2, TimeUnit.SECONDS);
            } catch (TimeoutException waitingForTheEditsLock) {
                // expected: it commits after the edit
            }
            return reserved;
        }).when(reservationRepository).sumUnreleasedQuantity(LISTING_ID);
        try {
            edit.run();
            for (Future<?> other : started) {
                try {
                    other.get(30, TimeUnit.SECONDS);
                } catch (ExecutionException rejected) {
                    // a reserve holding the pre-edit token loses after the edit; that is the correct outcome
                }
            }
        } finally {
            pool.shutdownNow();
        }
    }

    private void assertAvailableIsOfferedMinusReserved() {
        BigDecimal reservedTotal = jdbcTemplate.queryForObject("""
                SELECT COALESCE(SUM(reserved_quantity), 0) FROM listing_reservations
                WHERE listing_id = ? AND status <> 'RELEASED'""", BigDecimal.class, LISTING_ID.value());
        Map<String, Object> row = row();
        assertThat((BigDecimal) row.get("quantity_available"))
                .isEqualByComparingTo(((BigDecimal) row.get("quantity")).subtract(reservedTotal));
    }

    @Test
    void releaseCommittingDuringTheEdit_isNotLost() throws Exception {
        reserve("contract-1", "20000");

        duringTheEdit(() -> {
            release("contract-1", "20000");
            return null;
        }, () -> edit("60000"));

        assertAvailableIsOfferedMinusReserved();
        assertThat((BigDecimal) row().get("quantity_available")).isEqualByComparingTo("60000");
        assertThat(row()).containsEntry("status", "AVAILABLE");
    }

    @Test
    void reserveCommittingDuringTheEdit_isNotLost() throws Exception {
        String tokenBeforeTheEdit = token();

        duringTheEdit(() -> reserveUseCase.execute(new ReserveListingQuantityRequest("contract-1",
                LISTING_ID.value(), new BigDecimal("20000"), tokenBeforeTheEdit)), () -> edit("60000"));

        assertAvailableIsOfferedMinusReserved();
    }

    /**
     * The edit locks the row and counts reservations in the same transaction, so racing reserves and releases can
     * never leave quantityAvailable different from offeredQuantity minus what is still reserved.
     */
    @Test
    void editRacingReserveAndRelease_keepsAvailableEqualToOfferedMinusReserved() throws Exception {
        // releases do not depend on the version token, so they keep landing while edits run
        List<String> reserved = new CopyOnWriteArrayList<>();
        for (int i = 0; i < 30; i++) {
            reserve("held-" + i, "1000");
            reserved.add("held-" + i);
        }
        ExecutorService pool = Executors.newFixedThreadPool(3);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<?> editor = pool.submit(() -> {
                start.await();
                for (int i = 0; i < 40; i++) {
                    edit(i % 2 == 0 ? "60000" : "45000");
                }
                return null;
            });
            Future<?> buyer = pool.submit(() -> {
                start.await();
                for (int i = 0; i < 40; i++) {
                    try {
                        reserve("contract-" + i, "1000");
                    } catch (ReservationConflictException staleTokenOrNoStock) {
                        // an edit won the row first; the buyer would reload
                    }
                }
                return null;
            });
            Future<?> compensator = pool.submit(() -> {
                start.await();
                for (String contractId : reserved) {
                    release(contractId, "1000");
                }
                return null;
            });
            start.countDown();
            for (Future<?> f : List.of(editor, buyer, compensator)) {
                f.get(60, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }

        BigDecimal reservedTotal = jdbcTemplate.queryForObject("""
                SELECT COALESCE(SUM(reserved_quantity), 0) FROM listing_reservations
                WHERE listing_id = ? AND status <> 'RELEASED'""", BigDecimal.class, LISTING_ID.value());
        Map<String, Object> row = row();
        assertThat((BigDecimal) row.get("quantity_available"))
                .isEqualByComparingTo(((BigDecimal) row.get("quantity")).subtract(reservedTotal));
        assertThat(row.get("status")).isEqualTo(reservedTotal.signum() == 0 ? "AVAILABLE" : "PARTIALLY_COMMITTED");
    }

    @Test
    void sequentialReserveEditRelease_holdTheInvariantAtEveryStep() {
        List<String> trace = new ArrayList<>();
        reserve("contract-1", "20000");
        trace.add(edit("60000").quantityAvailable().toPlainString());
        reserve("contract-2", "10000");
        trace.add(String.valueOf(row().get("quantity_available")));
        release("contract-1", "20000");
        trace.add(String.valueOf(row().get("quantity_available")));
        trace.add(edit("10000").quantityAvailable().toPlainString());

        assertThat(trace).extracting(BigDecimal::new)
                .usingElementComparator(BigDecimal::compareTo)
                .containsExactly(new BigDecimal("40000"), new BigDecimal("30000"), new BigDecimal("50000"),
                        BigDecimal.ZERO);
        assertThat(row()).containsEntry("status", "CLOSED").containsEntry("close_reason", "SELLER_CLOSED");
    }
}
