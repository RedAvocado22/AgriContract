package com.agricontract.user.infrastructure.persistence.repository;

import com.agricontract.user.domain.model.UserProfile;
import com.agricontract.user.domain.model.vo.ContactInfo;
import com.agricontract.user.domain.model.vo.Role;
import com.agricontract.user.domain.model.vo.UserId;
import com.agricontract.user.domain.repository.UserProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * V6 locked_until against real MySQL: the projection is read back exactly, and a KYC save
 * never overwrites a lock written by the reputation event consumer.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers(disabledWithoutDocker = true)
class UserProfileRepositoryImplLockProjectionTest {

    @Container
    @ServiceConnection
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0");

    private static final UserId USER_ID = new UserId("seller-locked");
    private static final Instant LOCKED_UNTIL = Instant.parse("2026-11-01T00:00:00.123456Z");

    @Autowired private UserProfileRepository userProfileRepository;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private PlatformTransactionManager transactionManager;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM user_profiles");
        userProfileRepository.save(UserProfile.create(USER_ID, "HTX Ca Phe", Role.SELLER,
                new ContactInfo("seller-locked@agricontract.test", null, null)));
    }

    @Test
    void newProfile_isNotLocked() {
        assertThat(userProfileRepository.findById(USER_ID).orElseThrow().getLockedUntil()).isNull();
    }

    @Test
    void findById_readsLockProjection() {
        lockAt(LOCKED_UNTIL);

        assertThat(userProfileRepository.findById(USER_ID).orElseThrow().getLockedUntil()).isEqualTo(LOCKED_UNTIL);
    }

    @Test
    void kycDecisionUpdate_keepsLockWrittenAfterLoad() {
        new TransactionTemplate(transactionManager).executeWithoutResult(tx -> {
            // Same load-then-update shape as VerifyUserUseCaseImpl: the managed entity still holds locked_until = NULL.
            UserProfile profile = userProfileRepository.findById(USER_ID).orElseThrow();
            lockAt(LOCKED_UNTIL);

            profile.verify("operator-1", Instant.parse("2026-10-20T03:00:00Z"), null);
            userProfileRepository.update(profile);
        });

        assertThat(userProfileRepository.findById(USER_ID).orElseThrow().getLockedUntil()).isEqualTo(LOCKED_UNTIL);
    }

    // Hibernate stores Instant as UTC wall time, so the raw write must use UTC too.
    private void lockAt(Instant lockedUntil) {
        jdbcTemplate.update("UPDATE user_profiles SET locked_until = ? WHERE user_id = ?",
                LocalDateTime.ofInstant(lockedUntil, ZoneOffset.UTC), USER_ID.value());
    }
}
