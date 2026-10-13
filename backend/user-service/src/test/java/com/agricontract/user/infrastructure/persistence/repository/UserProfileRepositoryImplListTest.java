package com.agricontract.user.infrastructure.persistence.repository;

import com.agricontract.user.domain.model.UserProfile;
import com.agricontract.user.domain.model.vo.ContactInfo;
import com.agricontract.user.domain.model.vo.Role;
import com.agricontract.user.domain.model.vo.UserId;
import com.agricontract.user.domain.model.vo.VerificationStatus;
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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * KYC queue query against real MySQL: status filter, oldest-first order and plain page/size
 * translated to LIMIT/OFFSET in the persistence adapter.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers(disabledWithoutDocker = true)
class UserProfileRepositoryImplListTest {

    @Container
    @ServiceConnection
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0");

    @Autowired private UserProfileRepository userProfileRepository;
    @Autowired private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM user_profiles");
        // Inserted in one order, created_at set to another, so the test proves ordering is by created_at, not id.
        register("seller-c", "2026-10-03 08:00:00");
        register("seller-a", "2026-10-01 08:00:00");
        register("seller-b", "2026-10-02 08:00:00");
        register("seller-rejected", "2026-09-01 08:00:00");
        jdbcTemplate.update("UPDATE user_profiles SET verification_status = 'REJECTED' WHERE user_id = ?",
                "seller-rejected");
    }

    @Test
    void findByVerificationStatus_returnsOnlyThatStatus_oldestFirst() {
        assertThat(userIds(userProfileRepository.findByVerificationStatus(VerificationStatus.PENDING, 0, 20)))
                .containsExactly("seller-a", "seller-b", "seller-c");
        assertThat(userProfileRepository.countByVerificationStatus(VerificationStatus.PENDING)).isEqualTo(3);
    }

    @Test
    void findByVerificationStatus_pagesWithPlainPageAndSize() {
        assertThat(userIds(userProfileRepository.findByVerificationStatus(VerificationStatus.PENDING, 0, 2)))
                .containsExactly("seller-a", "seller-b");
        assertThat(userIds(userProfileRepository.findByVerificationStatus(VerificationStatus.PENDING, 1, 2)))
                .containsExactly("seller-c");
        assertThat(userProfileRepository.findByVerificationStatus(VerificationStatus.PENDING, 2, 2)).isEmpty();
    }

    @Test
    void findByVerificationStatus_otherStatus() {
        assertThat(userIds(userProfileRepository.findByVerificationStatus(VerificationStatus.REJECTED, 0, 20)))
                .containsExactly("seller-rejected");
        assertThat(userProfileRepository.countByVerificationStatus(VerificationStatus.REJECTED)).isEqualTo(1);
        assertThat(userProfileRepository.countByVerificationStatus(VerificationStatus.VERIFIED)).isZero();
    }

    private void register(String userId, String createdAt) {
        userProfileRepository.save(UserProfile.create(new UserId(userId), "HTX Ca Phe", Role.SELLER,
                new ContactInfo(userId + "@agricontract.test", null, null)));
        jdbcTemplate.update("UPDATE user_profiles SET created_at = ? WHERE user_id = ?", createdAt, userId);
    }

    private static List<String> userIds(List<UserProfile> profiles) {
        return profiles.stream().map(p -> p.getUserId().value()).toList();
    }
}
