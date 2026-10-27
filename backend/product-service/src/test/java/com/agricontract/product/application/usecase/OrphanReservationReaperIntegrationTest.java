package com.agricontract.product.application.usecase;

import com.agricontract.product.application.dto.ContractCreatedCommand;
import com.agricontract.product.application.dto.ReserveListingQuantityRequest;
import com.agricontract.product.domain.model.Listing;
import com.agricontract.product.domain.model.vo.*;
import com.agricontract.product.domain.repository.ListingRepository;
import com.agricontract.product.domain.repository.ReservationRepository;
import com.agricontract.product.infrastructure.config.ClockConfig;
import com.agricontract.product.infrastructure.persistence.mapper.ListingMapperImpl;
import com.agricontract.product.infrastructure.persistence.mapper.PlotRegistryMapperImpl;
import com.agricontract.product.infrastructure.persistence.mapper.ReservationMapperImpl;
import com.agricontract.product.infrastructure.persistence.repository.ListingRepositoryImpl;
import com.agricontract.product.infrastructure.persistence.repository.ProcessedContractEventRepositoryImpl;
import com.agricontract.product.infrastructure.persistence.repository.ReservationRepositoryImpl;
import com.agricontract.product.testsupport.GoodsFixtures;
import com.agricontract.product.testsupport.PlotFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Orphan reservation reaper and the contract.created late-event race on real MySQL (matrix 28c): only RESERVED
 * past the grace period without a recorded contract.created is released, exactly once; SIGNED_RELEASABLE and
 * COMMITTED are untouched; a seller-closed listing gets the counter back but stays closed; contract.created after
 * the release is logged and alerted once, with no re-decrement or reverse transition.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({ListingRepositoryImpl.class, ReservationRepositoryImpl.class, ProcessedContractEventRepositoryImpl.class,
        ListingMapperImpl.class, ReservationMapperImpl.class, PlotRegistryMapperImpl.class, ClockConfig.class,
        ReserveListingQuantityUseCaseImpl.class, ReservationReleaser.class,
        ReapOrphanReservationsUseCaseImpl.class, ConfirmContractCreatedUseCaseImpl.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Testcontainers(disabledWithoutDocker = true)
@ExtendWith(OutputCaptureExtension.class)
class OrphanReservationReaperIntegrationTest {

    @Container
    @ServiceConnection
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0");

    private static final ListingId LISTING_ID = new ListingId("listing-1");
    private static final Duration GRACE = Duration.ofMinutes(30);
    private static final String RELEASED_ALERT = "event=orphan_reservation_released alert=OPERATOR";
    private static final String LATE_EVENT_ALERT = "event=contract_created_after_release alert=OPERATOR";

    @Autowired private ReserveListingQuantityUseCase reserveUseCase;
    @Autowired private ReapOrphanReservationsUseCase reaper;
    @Autowired private ConfirmContractCreatedUseCase confirmContractCreated;
    @Autowired private ListingRepository listingRepository;
    @Autowired private ReservationRepository reservationRepository;
    @Autowired private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM processed_contract_events");
        PlotFixtures.deletePlotsAndListings(jdbcTemplate);
        PlotFixtures.insertRegistryEntry(jdbcTemplate, PlotFixtures.PLOT_ID, "seller-1");
        listingRepository.save(Listing.create(LISTING_ID, "seller-1", new ProductId("product-1"),
                "Ca phe Robusta", "https://cdn.test/cover.jpg", new Quantity(new BigDecimal("100"), "kg"),
                new BigDecimal("10"), new Money(new BigDecimal("50000"), "VND"), LocalDate.of(2026, 12, 31),
                PlotFixtures.onePlot(), GoodsFixtures.coffeeGoods()));
    }

    @Test
    void reap_releasesStaleOrphanOnce(CapturedOutput output) {
        reserve("contract-1", "40");
        age("contract-1", GRACE.plusMinutes(1));

        assertThat(reap()).isEqualTo(1);
        assertThat(reap()).isZero();

        assertThat(status("contract-1")).isEqualTo(ReservationStatus.RELEASED);
        assertThat(available()).isEqualByComparingTo("100");
        assertThat(occurrences(output, RELEASED_ALERT)).isEqualTo(1);
    }

    @Test
    void reap_keepsReservationInsideGracePeriod() {
        reserve("contract-1", "40");
        age("contract-1", GRACE.minusMinutes(1));

        assertThat(reap()).isZero();

        assertThat(status("contract-1")).isEqualTo(ReservationStatus.RESERVED);
        assertThat(available()).isEqualByComparingTo("60");
    }

    @Test
    void reap_skipsReservationWhoseContractCreatedWasRecorded() {
        reserve("contract-1", "40");
        confirmContractCreated.execute(contractCreated(UUID.randomUUID(), "contract-1", "40"));
        age("contract-1", GRACE.plusMinutes(1));

        assertThat(reap()).isZero();

        assertThat(status("contract-1")).isEqualTo(ReservationStatus.RESERVED);
        assertThat(available()).isEqualByComparingTo("60");
    }

    @Test
    void reap_neverTouchesSignedOrCommitted() {
        reserve("contract-signed", "40");
        reserve("contract-committed", "40");
        reservationRepository.transition(new ContractId("contract-signed"),
                ReservationStatus.RESERVED, ReservationStatus.SIGNED_RELEASABLE);
        reservationRepository.transition(new ContractId("contract-committed"),
                ReservationStatus.RESERVED, ReservationStatus.SIGNED_RELEASABLE);
        reservationRepository.transition(new ContractId("contract-committed"),
                ReservationStatus.SIGNED_RELEASABLE, ReservationStatus.COMMITTED);
        age("contract-signed", GRACE.plusMinutes(1));
        age("contract-committed", GRACE.plusMinutes(1));

        assertThat(reap()).isZero();

        assertThat(status("contract-signed")).isEqualTo(ReservationStatus.SIGNED_RELEASABLE);
        assertThat(status("contract-committed")).isEqualTo(ReservationStatus.COMMITTED);
        assertThat(available()).isEqualByComparingTo("20");
    }

    @Test
    void reap_sellerClosedListing_restoresCounterButStaysClosed() {
        reserve("contract-1", "40");
        Listing listing = listingRepository.findById(LISTING_ID).orElseThrow();
        listing.close();
        listingRepository.save(listing);
        age("contract-1", GRACE.plusMinutes(1));

        assertThat(reap()).isEqualTo(1);

        Listing after = listingRepository.findById(LISTING_ID).orElseThrow();
        assertThat(after.getStatus()).isEqualTo(ListingStatus.CLOSED);
        assertThat(after.getQuantityAvailable()).isEqualByComparingTo("100");
    }

    @Test
    void reap_concurrentRuns_restoreOnce() throws Exception {
        reserve("contract-1", "40");
        age("contract-1", GRACE.plusMinutes(1));

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Callable<Integer> run = () -> {
                start.await();
                return reap();
            };
            Future<Integer> a = pool.submit(run);
            Future<Integer> b = pool.submit(run);
            start.countDown();
            assertThat(a.get(30, TimeUnit.SECONDS) + b.get(30, TimeUnit.SECONDS)).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
        assertThat(available()).isEqualByComparingTo("100");
    }

    @Test
    void lateContractCreated_afterReap_logsAndAlertsOnce_withoutTouchingInventory(CapturedOutput output) {
        reserve("contract-1", "40");
        age("contract-1", GRACE.plusMinutes(1));
        reap();
        UUID eventId = UUID.randomUUID();

        confirmContractCreated.execute(contractCreated(eventId, "contract-1", "40"));
        confirmContractCreated.execute(contractCreated(eventId, "contract-1", "40"));

        assertThat(occurrences(output, LATE_EVENT_ALERT)).isEqualTo(1);
        assertThat(output.getAll()).contains("eventId=" + eventId);
        assertThat(status("contract-1")).isEqualTo(ReservationStatus.RELEASED);
        assertThat(available()).isEqualByComparingTo("100");
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM processed_contract_events WHERE event_id = ?",
                Integer.class, eventId.toString())).isEqualTo(1);
    }

    @Test
    void contractCreated_onReservedReservation_isQuietConfirmation(CapturedOutput output) {
        reserve("contract-1", "40");

        confirmContractCreated.execute(contractCreated(UUID.randomUUID(), "contract-1", "40"));

        assertThat(output.getAll()).doesNotContain("alert=OPERATOR");
        assertThat(status("contract-1")).isEqualTo(ReservationStatus.RESERVED);
        assertThat(available()).isEqualByComparingTo("60");
    }

    @Test
    void contractCreated_withoutReservation_alerts(CapturedOutput output) {
        confirmContractCreated.execute(contractCreated(UUID.randomUUID(), "contract-x", "40"));

        assertThat(output.getAll()).contains("event=contract_created_without_reservation alert=OPERATOR");
    }

    // ---------- helpers ----------

    private int reap() {
        return reaper.execute(Instant.now().minus(GRACE));
    }

    private void reserve(String contractId, String quantity) {
        String token = listingRepository.findById(LISTING_ID).orElseThrow().getVersionToken();
        reserveUseCase.execute(new ReserveListingQuantityRequest(
                contractId, LISTING_ID.value(), new BigDecimal(quantity), token));
    }

    private void age(String contractId, Duration age) {
        jdbcTemplate.update("UPDATE listing_reservations SET created_at = created_at - INTERVAL ? MINUTE "
                + "WHERE contract_id = ?", age.toMinutes(), contractId);
    }

    private static ContractCreatedCommand contractCreated(UUID eventId, String contractId, String quantity) {
        return new ContractCreatedCommand(eventId, contractId, LISTING_ID.value(), new BigDecimal(quantity));
    }

    private ReservationStatus status(String contractId) {
        return reservationRepository.findByContractId(new ContractId(contractId)).orElseThrow().getStatus();
    }

    private BigDecimal available() {
        return listingRepository.findById(LISTING_ID).orElseThrow().getQuantityAvailable();
    }

    private static int occurrences(CapturedOutput output, String text) {
        return output.getAll().split(java.util.regex.Pattern.quote(text), -1).length - 1;
    }
}
