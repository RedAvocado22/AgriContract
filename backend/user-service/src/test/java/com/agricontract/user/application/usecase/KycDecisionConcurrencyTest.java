package com.agricontract.user.application.usecase;

import com.agricontract.user.application.dto.RejectUserCommand;
import com.agricontract.user.application.dto.VerifyUserCommand;
import com.agricontract.user.domain.exception.ConcurrentProfileUpdateException;
import com.agricontract.user.domain.model.UserProfile;
import com.agricontract.user.domain.model.vo.ContactInfo;
import com.agricontract.user.domain.model.vo.Role;
import com.agricontract.user.domain.model.vo.UserId;
import com.agricontract.user.domain.model.vo.VerificationStatus;
import com.agricontract.user.domain.repository.UserProfileRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Two KYC decisions on the same PENDING profile, run against real MySQL. Both use cases
 * read the clock after loading the profile and before writing it, so a barrier in the
 * clock guarantees both transactions have read PENDING before either one writes.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers(disabledWithoutDocker = true)
class KycDecisionConcurrencyTest {

    private static final Instant NOW = Instant.parse("2026-10-07T03:00:00Z");
    private static final Instant EXPIRES_AT = Instant.parse("2027-06-30T16:59:59.999Z");

    @Container
    @ServiceConnection
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0");

    @TestConfiguration
    static class BarrierClockConfig {
        @Bean
        @Primary
        BarrierClock barrierClock() {
            return new BarrierClock();
        }
    }

    @Autowired private BarrierClock clock;
    @Autowired private UserProfileRepository userProfileRepository;
    @Autowired private VerifyUserUseCase verifyUserUseCase;
    @Autowired private RejectUserUseCase rejectUserUseCase;
    @Autowired private JdbcTemplate jdbc;

    private final ExecutorService executor = Executors.newFixedThreadPool(2);

    @AfterEach
    void tearDown() {
        clock.barrier = null;
        executor.shutdownNow();
    }

    @Test
    void verifyAndReject_concurrently_onlyOneDecisionIsStored() throws Exception {
        String targetId = register("seller-race-1");

        List<Outcome> outcomes = race(
                () -> verifyUserUseCase.execute(new VerifyUserCommand(targetId, "operator-a", EXPIRES_AT)),
                () -> rejectUserUseCase.execute(new RejectUserCommand(targetId, "operator-b", "Blurry ID scan")));

        assertOneWinnerOneConflict(outcomes);
        assertOneCommandFor(targetId);
        UserProfile stored = userProfileRepository.findById(new UserId(targetId)).orElseThrow();
        if (outcomes.get(0).succeeded()) {
            assertThat(stored.getVerificationStatus()).isEqualTo(VerificationStatus.VERIFIED);
            assertThat(stored.getVerifiedByActorId()).isEqualTo("operator-a");
            assertThat(stored.getAuthorizationExpiresAt()).isEqualTo(EXPIRES_AT);
            assertThat(stored.getRejectionReason()).isNull();
        } else {
            assertThat(stored.getVerificationStatus()).isEqualTo(VerificationStatus.REJECTED);
            assertThat(stored.getVerifiedByActorId()).isEqualTo("operator-b");
            assertThat(stored.getRejectionReason()).isEqualTo("Blurry ID scan");
            assertThat(stored.getAuthorizationExpiresAt()).isNull();
        }
    }

    @Test
    void verifyTwice_concurrently_secondReviewerGetsConflict() throws Exception {
        String targetId = register("seller-race-2");

        List<Outcome> outcomes = race(
                () -> verifyUserUseCase.execute(new VerifyUserCommand(targetId, "operator-a", EXPIRES_AT)),
                () -> verifyUserUseCase.execute(new VerifyUserCommand(targetId, "operator-b", null)));

        assertOneWinnerOneConflict(outcomes);
        assertOneCommandFor(targetId);
        UserProfile stored = userProfileRepository.findById(new UserId(targetId)).orElseThrow();
        String winner = outcomes.get(0).succeeded() ? "operator-a" : "operator-b";
        assertThat(stored.getVerificationStatus()).isEqualTo(VerificationStatus.VERIFIED);
        assertThat(stored.getVerifiedByActorId()).isEqualTo(winner);
    }

    private String register(String userId) {
        userProfileRepository.save(UserProfile.create(new UserId(userId), "HTX Ca Phe", Role.SELLER,
                new ContactInfo(userId + "@agricontract.test", null, null)));
        return userId;
    }

    private List<Outcome> race(Callable<?> first, Callable<?> second) throws InterruptedException {
        clock.barrier = new CyclicBarrier(2);
        List<Future<?>> futures = List.of(executor.submit(first), executor.submit(second));
        List<Outcome> outcomes = new ArrayList<>();
        for (Future<?> future : futures) {
            try {
                future.get(60, TimeUnit.SECONDS);
                outcomes.add(new Outcome(null));
            } catch (ExecutionException e) {
                outcomes.add(new Outcome(e.getCause()));
            } catch (java.util.concurrent.TimeoutException e) {
                throw new AssertionError("Decision did not finish", e);
            }
        }
        return outcomes;
    }

    private void assertOneWinnerOneConflict(List<Outcome> outcomes) {
        assertThat(outcomes).filteredOn(Outcome::succeeded).hasSize(1);
        assertThat(outcomes).filteredOn(o -> !o.succeeded()).singleElement()
                .extracting(Outcome::failure)
                .isInstanceOf(ConcurrentProfileUpdateException.class);
    }

    // The losing transaction rolls back, taking its outbox row with it.
    private void assertOneCommandFor(String targetId) {
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM event_outbox WHERE event_type = 'notification.user_kyc_result_requested'"
                        + " AND aggregate_id = ?", Integer.class, targetId)).isEqualTo(1);
    }

    private record Outcome(Throwable failure) {
        boolean succeeded() {
            return failure == null;
        }
    }

    static class BarrierClock extends Clock {
        volatile CyclicBarrier barrier;

        @Override
        public Instant instant() {
            CyclicBarrier current = barrier;
            if (current != null) {
                try {
                    current.await(30, TimeUnit.SECONDS);
                } catch (Exception e) {
                    throw new IllegalStateException("Both decisions must load the profile before either writes", e);
                }
            }
            return NOW;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }
    }
}
