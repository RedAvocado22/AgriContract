package com.agricontract.product.application.usecase;

import com.agricontract.product.application.dto.ContractLifecycleCommand;
import com.agricontract.product.application.dto.ReleaseListingReservationRequest;
import com.agricontract.product.application.dto.ReserveListingQuantityRequest;
import com.agricontract.product.application.exception.ReservationNotReadyException;
import com.agricontract.product.common.exception.InventoryInvariantViolationException;
import com.agricontract.product.domain.model.Listing;
import com.agricontract.product.domain.model.vo.*;
import com.agricontract.product.domain.repository.ListingRepository;
import com.agricontract.product.domain.repository.ReservationRepository;
import com.agricontract.product.infrastructure.config.ClockConfig;
import com.agricontract.product.infrastructure.persistence.SpringTransactionRunner;
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
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;

import static com.agricontract.product.domain.model.vo.ContractLifecycleEventType.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Reservation transitions from contract lifecycle events on real MySQL (matrix 11s, 28b): signed does not commit
 * inventory, activation commits once, activation failure and withdraw restore once, a seller-closed listing gets
 * the counter back but stays closed, and states an event cannot follow are alerted without touching inventory.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({ListingRepositoryImpl.class, ReservationRepositoryImpl.class, ProcessedContractEventRepositoryImpl.class,
        ListingMapperImpl.class, ReservationMapperImpl.class, PlotRegistryMapperImpl.class, ClockConfig.class,
        ReserveListingQuantityUseCaseImpl.class, ReleaseListingReservationUseCaseImpl.class, ReservationReleaser.class,
        ApplyContractLifecycleEventUseCaseImpl.class, SpringTransactionRunner.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Testcontainers(disabledWithoutDocker = true)
@ExtendWith(OutputCaptureExtension.class)
class ContractLifecycleIntegrationTest {

    @Container
    @ServiceConnection
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0");

    private static final ListingId LISTING_ID = new ListingId("listing-1");
    private static final String CONTRACT = "contract-1";

    @Autowired private ReserveListingQuantityUseCase reserveUseCase;
    @Autowired private ReleaseListingReservationUseCase compensation;
    @Autowired private ApplyContractLifecycleEventUseCase lifecycle;
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
        reserve(CONTRACT, "40");
    }

    @Test
    void signed_movesToSignedReleasable_withoutCommittingInventory(CapturedOutput output) {
        apply(CONTRACT_SIGNED);

        assertThat(status()).isEqualTo(ReservationStatus.SIGNED_RELEASABLE);
        assertThat(available()).isEqualByComparingTo("60");
        assertThat(output.getAll()).doesNotContain("alert=OPERATOR");
    }

    @Test
    void activated_commitsOnce_andAReplayChangesNothing() {
        apply(CONTRACT_SIGNED);
        UUID eventId = UUID.randomUUID();

        lifecycle.execute(command(eventId, CONTRACT_ACTIVATED, "40"));
        lifecycle.execute(command(eventId, CONTRACT_ACTIVATED, "40"));
        apply(CONTRACT_ACTIVATED);

        assertThat(status()).isEqualTo(ReservationStatus.COMMITTED);
        assertThat(available()).isEqualByComparingTo("60");
        assertThat(recorded(eventId)).isEqualTo(1);
    }

    @Test
    void activatedBeforeSigned_isRetried_withNothingRecorded_thenAppliesInOrder() {
        UUID activated = UUID.randomUUID();

        assertThatThrownBy(() -> lifecycle.execute(command(activated, CONTRACT_ACTIVATED, "40")))
                .isInstanceOf(ReservationNotReadyException.class);
        assertThat(status()).isEqualTo(ReservationStatus.RESERVED);
        assertThat(recorded(activated)).isZero();

        apply(CONTRACT_SIGNED);
        lifecycle.execute(command(activated, CONTRACT_ACTIVATED, "40"));

        assertThat(status()).isEqualTo(ReservationStatus.COMMITTED);
    }

    @Test
    void activationFailed_releasesASignedReservation_restoringOnce_evenWhenRedeliveredOrRepeated() {
        apply(CONTRACT_SIGNED);
        UUID eventId = UUID.randomUUID();

        lifecycle.execute(command(eventId, CONTRACT_ACTIVATION_FAILED, "40"));
        lifecycle.execute(command(eventId, CONTRACT_ACTIVATION_FAILED, "40"));
        apply(CONTRACT_ACTIVATION_FAILED);

        assertThat(status()).isEqualTo(ReservationStatus.RELEASED);
        assertThat(available()).isEqualByComparingTo("100");
    }

    @Test
    void activationFailedOvertakingSigned_releases_andTheLateSignedIsQuiet(CapturedOutput output) {
        apply(CONTRACT_ACTIVATION_FAILED);
        apply(CONTRACT_SIGNED);

        assertThat(status()).isEqualTo(ReservationStatus.RELEASED);
        assertThat(available()).isEqualByComparingTo("100");
        assertThat(output.getAll()).doesNotContain("alert=OPERATOR");
    }

    @Test
    void activationFailedAfterCommit_isAnIncident_andRestoresNothing(CapturedOutput output) {
        apply(CONTRACT_SIGNED);
        apply(CONTRACT_ACTIVATED);
        UUID eventId = UUID.randomUUID();

        lifecycle.execute(command(eventId, CONTRACT_ACTIVATION_FAILED, "40"));
        lifecycle.execute(command(eventId, CONTRACT_ACTIVATION_FAILED, "40"));

        assertThat(status()).isEqualTo(ReservationStatus.COMMITTED);
        assertThat(available()).isEqualByComparingTo("60");
        assertThat(occurrences(output, "event=contract_activation_failed_after_commit alert=OPERATOR")).isEqualTo(1);
    }

    @Test
    void withdrawn_releasesOnce() {
        UUID eventId = UUID.randomUUID();

        lifecycle.execute(command(eventId, CONTRACT_WITHDRAWN, "40"));
        lifecycle.execute(command(eventId, CONTRACT_WITHDRAWN, "40"));

        assertThat(status()).isEqualTo(ReservationStatus.RELEASED);
        assertThat(available()).isEqualByComparingTo("100");
    }

    @Test
    void withdrawn_onASellerClosedListing_restoresTheCounterButKeepsItClosed() {
        Listing listing = listingRepository.findById(LISTING_ID).orElseThrow();
        listing.close();
        listingRepository.save(listing);

        apply(CONTRACT_WITHDRAWN);

        Listing after = listingRepository.findById(LISTING_ID).orElseThrow();
        assertThat(after.getStatus()).isEqualTo(ListingStatus.CLOSED);
        assertThat(after.getQuantityAvailable()).isEqualByComparingTo("100");
    }

    @Test
    void withdrawnAfterACompensationRelease_isQuiet_andRestoresNothingMore(CapturedOutput output) {
        compensation.execute(CONTRACT, new ReleaseListingReservationRequest(LISTING_ID.value(), new BigDecimal("40"),
                "CREATE_PERSISTENCE_COMPENSATION"));

        apply(CONTRACT_WITHDRAWN);

        assertThat(available()).isEqualByComparingTo("100");
        assertThat(output.getAll()).doesNotContain("alert=OPERATOR");
    }

    @Test
    void withdrawnAfterSigned_isAnIncident_andRestoresNothing(CapturedOutput output) {
        apply(CONTRACT_SIGNED);

        apply(CONTRACT_WITHDRAWN);

        assertThat(status()).isEqualTo(ReservationStatus.SIGNED_RELEASABLE);
        assertThat(available()).isEqualByComparingTo("60");
        assertThat(output.getAll()).contains("event=contract_withdrawn_after_sign alert=OPERATOR");
    }

    @Test
    void signedOnAReleasedReservation_withoutActivationFailure_isAnIncident(CapturedOutput output) {
        apply(CONTRACT_WITHDRAWN);

        apply(CONTRACT_SIGNED);

        assertThat(status()).isEqualTo(ReservationStatus.RELEASED);
        assertThat(output.getAll()).contains("event=contract_signed_after_release alert=OPERATOR");
    }

    @Test
    void eventThatDoesNotMatchTheReservation_isAlerted_andChangesNothing(CapturedOutput output) {
        lifecycle.execute(command(UUID.randomUUID(), CONTRACT_WITHDRAWN, "39"));

        assertThat(status()).isEqualTo(ReservationStatus.RESERVED);
        assertThat(available()).isEqualByComparingTo("60");
        assertThat(output.getAll()).contains("event=contract_withdrawn_reservation_mismatch alert=OPERATOR");
    }

    @Test
    void eventWithoutReservation_isAlerted(CapturedOutput output) {
        lifecycle.execute(new ContractLifecycleCommand(UUID.randomUUID(), CONTRACT_SIGNED, "contract-x",
                LISTING_ID.value(), new BigDecimal("40")));

        assertThat(output.getAll()).contains("event=contract_signed_without_reservation alert=OPERATOR");
    }

    // transition, counter restore and the processed-event row commit together or not at all
    @Test
    void failedRelease_rollsBackTransitionAndDedupRow_soRedeliveryApplies() {
        UUID eventId = UUID.randomUUID();
        // restoring 40 onto a full listing would exceed what it offers
        jdbcTemplate.update("UPDATE listings SET quantity_available = 100 WHERE listing_id = ?", LISTING_ID.value());

        assertThatThrownBy(() -> lifecycle.execute(command(eventId, CONTRACT_WITHDRAWN, "40")))
                .isInstanceOf(InventoryInvariantViolationException.class);

        assertThat(status()).isEqualTo(ReservationStatus.RESERVED);
        assertThat(recorded(eventId)).isZero();

        jdbcTemplate.update("UPDATE listings SET quantity_available = 60 WHERE listing_id = ?", LISTING_ID.value());
        lifecycle.execute(command(eventId, CONTRACT_WITHDRAWN, "40"));

        assertThat(status()).isEqualTo(ReservationStatus.RELEASED);
        assertThat(available()).isEqualByComparingTo("100");
        assertThat(recorded(eventId)).isEqualTo(1);
    }

    @Test
    void concurrentWithdrawnAndActivationFailed_restoreOnce() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(4);
        try {
            CountDownLatch start = new CountDownLatch(1);
            List<Future<?>> runs = new ArrayList<>();
            for (ContractLifecycleEventType type : List.of(CONTRACT_WITHDRAWN, CONTRACT_WITHDRAWN,
                    CONTRACT_ACTIVATION_FAILED, CONTRACT_ACTIVATION_FAILED)) {
                runs.add(pool.submit(() -> {
                    start.await();
                    lifecycle.execute(command(UUID.randomUUID(), type, "40"));
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> run : runs) {
                run.get(30, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }

        assertThat(status()).isEqualTo(ReservationStatus.RELEASED);
        assertThat(available()).isEqualByComparingTo("100");
    }

    // ---------- helpers ----------

    private void apply(ContractLifecycleEventType type) {
        lifecycle.execute(command(UUID.randomUUID(), type, "40"));
    }

    private static ContractLifecycleCommand command(UUID eventId, ContractLifecycleEventType type, String quantity) {
        return new ContractLifecycleCommand(eventId, type, CONTRACT, LISTING_ID.value(), new BigDecimal(quantity));
    }

    private void reserve(String contractId, String quantity) {
        String token = listingRepository.findById(LISTING_ID).orElseThrow().getVersionToken();
        reserveUseCase.execute(new ReserveListingQuantityRequest(
                contractId, LISTING_ID.value(), new BigDecimal(quantity), token));
    }

    private ReservationStatus status() {
        return reservationRepository.findByContractId(new ContractId(CONTRACT)).orElseThrow().getStatus();
    }

    private BigDecimal available() {
        return listingRepository.findById(LISTING_ID).orElseThrow().getQuantityAvailable();
    }

    private int recorded(UUID eventId) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM processed_contract_events WHERE event_id = ?",
                Integer.class, eventId.toString());
    }

    private static int occurrences(CapturedOutput output, String text) {
        return output.getAll().split(java.util.regex.Pattern.quote(text), -1).length - 1;
    }
}
