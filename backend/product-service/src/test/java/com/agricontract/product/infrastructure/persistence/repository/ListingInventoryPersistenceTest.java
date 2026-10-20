package com.agricontract.product.infrastructure.persistence.repository;

import com.agricontract.product.domain.model.Listing;
import com.agricontract.product.domain.model.Reservation;
import com.agricontract.product.domain.model.vo.*;
import com.agricontract.product.domain.repository.ListingRepository;
import com.agricontract.product.domain.repository.ReservationRepository;
import com.agricontract.product.infrastructure.persistence.mapper.ListingMapperImpl;
import com.agricontract.product.infrastructure.persistence.mapper.ReservationMapperImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.*;

import static com.agricontract.product.domain.model.vo.ReservationStatus.*;
import static org.assertj.core.api.Assertions.*;

/**
 * Inventory counters and reservation identity against real MySQL: Flyway V4 schema, CHECK constraints,
 * unique contractId and the conditional updates that later back the reserve API (matrix 28).
 * Not transactional per test so concurrent callers really race on committed rows.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({ListingRepositoryImpl.class, ReservationRepositoryImpl.class,
        ListingMapperImpl.class, ReservationMapperImpl.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Testcontainers(disabledWithoutDocker = true)
class ListingInventoryPersistenceTest {

    @Container
    @ServiceConnection
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0");

    private static final ListingId LISTING_ID = new ListingId("listing-1");

    @Autowired private ListingRepository listingRepository;
    @Autowired private ReservationRepository reservationRepository;
    @Autowired private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM listing_reservations");
        jdbcTemplate.update("DELETE FROM listings");
        listingRepository.save(Listing.create(LISTING_ID, "seller-1", new ProductId("product-1"),
                "Ca phe Robusta", "https://cdn.test/cover.jpg", new Quantity(new BigDecimal("100"), "kg"), null,
                new Money(new BigDecimal("50000"), "VND"), LocalDate.of(2026, 12, 31)));
    }

    @Test
    void newListing_persistsQuantityAvailableEqualToOffered() {
        assertThat(available()).isEqualByComparingTo("100");
        assertThat(listingRepository.findById(LISTING_ID).orElseThrow().getQuantityAvailable())
                .isEqualByComparingTo("100");
    }

    @Test
    void reserve_partialFill_thenInsufficientFailsWithoutChange() {
        assertThat(reserve(new BigDecimal("40"))).isTrue();
        assertThat(available()).isEqualByComparingTo("60");
        assertThat(reserve(new BigDecimal("40"))).isTrue();
        assertThat(available()).isEqualByComparingTo("20");

        assertThat(reserve(new BigDecimal("30"))).isFalse();
        assertThat(available()).isEqualByComparingTo("20");

        assertThat(reserve(new BigDecimal("20"))).isTrue();
        assertThat(available()).isEqualByComparingTo("0");
    }

    @Test
    void legacyNullListing_failsClosedAndStaysNull() {
        jdbcTemplate.update("UPDATE listings SET quantity_available = NULL WHERE listing_id = ?", LISTING_ID.value());

        assertThat(listingRepository.findById(LISTING_ID).orElseThrow().getQuantityAvailable()).isNull();
        assertThat(reserve(BigDecimal.ONE)).isFalse();
        assertThat(listingRepository.releaseAvailable(LISTING_ID, BigDecimal.ONE)).isFalse();
        assertThat(available()).isNull();
    }

    @Test
    void release_neverExceedsOfferedQuantity() {
        reserve(new BigDecimal("40"));

        assertThat(listingRepository.releaseAvailable(LISTING_ID, new BigDecimal("41"))).isFalse();
        assertThat(listingRepository.releaseAvailable(LISTING_ID, new BigDecimal("40"))).isTrue();
        assertThat(available()).isEqualByComparingTo("100");
    }

    @Test
    void nonPositiveQuantity_isRejectedBeforeTouchingTheRow() {
        assertThatThrownBy(() -> reserve(BigDecimal.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> listingRepository.releaseAvailable(LISTING_ID, new BigDecimal("-5")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(available()).isEqualByComparingTo("100");
    }

    // MySQL CHECK violations (error 3819) are not translated to DataIntegrityViolationException,
    // so assert on the constraint name to prove which rule rejected the write.
    @Test
    void checkConstraints_rejectOutOfRangeCounters() {
        assertThatThrownBy(() -> jdbcTemplate.update(
                "UPDATE listings SET quantity_available = -1 WHERE listing_id = ?", LISTING_ID.value()))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("chk_listings_quantity_available");
        assertThatThrownBy(() -> jdbcTemplate.update(
                "UPDATE listings SET quantity_available = 100.001 WHERE listing_id = ?", LISTING_ID.value()))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("chk_listings_quantity_available");
        assertThatThrownBy(() -> jdbcTemplate.update("""
                INSERT INTO listing_reservations (contract_id, listing_id, reserved_quantity, status, created_at, updated_at)
                VALUES ('contract-x', ?, 0, 'RESERVED', NOW(), NOW())""", LISTING_ID.value()))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("chk_listing_reservations_reserved_quantity");
        assertThatThrownBy(() -> jdbcTemplate.update("""
                INSERT INTO listing_reservations (contract_id, listing_id, reserved_quantity, status, created_at, updated_at)
                VALUES ('contract-y', ?, 1, 'SOLD_OUT', NOW(), NOW())""", LISTING_ID.value()))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("chk_listing_reservations_status");
        assertThatThrownBy(() -> jdbcTemplate.update("""
                INSERT INTO listing_reservations (contract_id, listing_id, reserved_quantity, status, created_at, updated_at)
                VALUES ('contract-z', 'no-such-listing', 1, 'RESERVED', NOW(), NOW())"""))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("fk_listing_reservations_listing");
        assertThat(available()).isEqualByComparingTo("100");
    }

    @Test
    void lastStock_twoConcurrentDecrements_onlyOneWins() throws Exception {
        reserve(new BigDecimal("40"));
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        Callable<Boolean> reserveLast = () -> {
            start.await();
            return reserve(new BigDecimal("60"));
        };
        try {
            Future<Boolean> first = pool.submit(reserveLast);
            Future<Boolean> second = pool.submit(reserveLast);
            start.countDown();

            assertThat(List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(true, false);
        } finally {
            pool.shutdownNow();
        }
        assertThat(available()).isEqualByComparingTo("0");
        assertThat(statusAndReason()).isEqualTo("CLOSED/INVENTORY_EXHAUSTED");
    }

    @Test
    void reservation_roundTripsAndContractIdIsUnique() {
        ContractId contractId = new ContractId("contract-1");
        reservationRepository.create(Reservation.create(contractId, LISTING_ID, new BigDecimal("40")));

        Reservation stored = reservationRepository.findByContractId(contractId).orElseThrow();
        assertThat(stored.getStatus()).isEqualTo(RESERVED);
        assertThat(stored.isSameRequest(LISTING_ID, new BigDecimal("40"))).isTrue();

        assertThatThrownBy(() -> reservationRepository.create(
                Reservation.create(contractId, LISTING_ID, new BigDecimal("10"))))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM listing_reservations", Integer.class))
                .isEqualTo(1);
    }

    @Test
    void transition_isConditional_soReplayAppliesOnce() {
        ContractId contractId = new ContractId("contract-1");
        reservationRepository.create(Reservation.create(contractId, LISTING_ID, new BigDecimal("40")));

        assertThat(reservationRepository.transition(contractId, RESERVED, SIGNED_RELEASABLE)).isTrue();
        assertThat(reservationRepository.transition(contractId, RESERVED, SIGNED_RELEASABLE)).isFalse();
        assertThat(reservationRepository.transition(contractId, SIGNED_RELEASABLE, COMMITTED)).isTrue();
        // committed is the hard commit point: a late release no longer applies
        assertThat(reservationRepository.transition(contractId, SIGNED_RELEASABLE, RELEASED)).isFalse();

        assertThat(reservationRepository.findByContractId(contractId).orElseThrow().getStatus()).isEqualTo(COMMITTED);
        assertThatThrownBy(() -> reservationRepository.transition(contractId, COMMITTED, RELEASED))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void reserve_staleOrForeignToken_changesNothing() {
        assertThat(listingRepository.reserveAvailable(LISTING_ID, BigDecimal.ONE, "2020-01-01T00:00:00Z")).isFalse();
        assertThat(listingRepository.reserveAvailable(LISTING_ID, BigDecimal.ONE, "not-a-token")).isFalse();
        assertThat(available()).isEqualByComparingTo("100");
    }

    @Test
    void reserve_doesNotChangeVersionToken() {
        String before = token();
        reserve(new BigDecimal("40"));
        assertThat(token()).isEqualTo(before);
    }

    @Test
    void reserve_belowMinOrderQuantity_changesNothing() {
        jdbcTemplate.update("UPDATE listings SET min_order_quantity = 50 WHERE listing_id = ?", LISTING_ID.value());

        assertThat(reserve(new BigDecimal("49.999"))).isFalse();
        assertThat(reserve(new BigDecimal("50"))).isTrue();
        assertThat(available()).isEqualByComparingTo("50");
    }

    @Test
    void reserve_notActive_changesNothing() {
        jdbcTemplate.update("UPDATE listings SET status = 'EXPIRED' WHERE listing_id = ?", LISTING_ID.value());

        assertThat(reserve(BigDecimal.ONE)).isFalse();
        assertThat(available()).isEqualByComparingTo("100");
    }

    @Test
    void drainToZero_closesAsInventoryExhausted_andReleaseReopens() {
        assertThat(reserve(new BigDecimal("100"))).isTrue();
        assertThat(statusAndReason()).isEqualTo("CLOSED/INVENTORY_EXHAUSTED");

        assertThat(listingRepository.releaseAvailable(LISTING_ID, new BigDecimal("100"))).isTrue();
        assertThat(statusAndReason()).isEqualTo("ACTIVE/null");
        assertThat(available()).isEqualByComparingTo("100");
    }

    @Test
    void release_preservesCloseThatWasNotInventoryExhaustion() {
        reserve(new BigDecimal("40"));
        Listing listing = listingRepository.findById(LISTING_ID).orElseThrow();
        listing.close();
        listingRepository.save(listing);

        assertThat(listingRepository.releaseAvailable(LISTING_ID, new BigDecimal("40"))).isTrue();
        assertThat(statusAndReason()).isEqualTo("CLOSED/null");
        assertThat(available()).isEqualByComparingTo("100");
    }

    @Test
    void entitySave_neverWritesBackAStaleCounter() {
        Listing stale = listingRepository.findById(LISTING_ID).orElseThrow(); // read at 100
        reserve(new BigDecimal("40"));
        stale.close();
        listingRepository.save(stale);

        assertThat(available()).isEqualByComparingTo("60");
    }

    private boolean reserve(BigDecimal quantity) {
        return listingRepository.reserveAvailable(LISTING_ID, quantity, token());
    }

    private String token() {
        return listingRepository.findById(LISTING_ID).orElseThrow().getVersionToken();
    }

    private String statusAndReason() {
        return jdbcTemplate.queryForObject("SELECT CONCAT(status, '/', COALESCE(close_reason, 'null')) FROM listings WHERE listing_id = ?",
                String.class, LISTING_ID.value());
    }

    private BigDecimal available() {
        return jdbcTemplate.queryForObject("SELECT quantity_available FROM listings WHERE listing_id = ?",
                BigDecimal.class, LISTING_ID.value());
    }
}
