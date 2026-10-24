package com.agricontract.user.application.usecase;

import com.agricontract.user.application.dto.GetKycProfileCommand;
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

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Read-audit against real MySQL (Flyway V1-V5): every detail read leaves its own row, an unknown user leaves none.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers(disabledWithoutDocker = true)
class KycReadAuditIntegrationTest {

    private static final String TARGET_ID = "seller-audit";

    @Container
    @ServiceConnection
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0");

    @Autowired private GetKycProfileUseCase getKycProfileUseCase;
    @Autowired private UserProfileRepository userProfileRepository;
    @Autowired private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM kyc_read_audit");
        jdbcTemplate.update("DELETE FROM user_profiles");
        userProfileRepository.save(UserProfile.create(new UserId(TARGET_ID), "HTX Ca Phe", Role.SELLER,
                new ContactInfo("seller-audit@agricontract.test", null, null)));
    }

    @Test
    void eachDetailRead_appendsOneRow() {
        getKycProfileUseCase.execute(new GetKycProfileCommand(TARGET_ID, "operator-1", "OPERATOR"));
        getKycProfileUseCase.execute(new GetKycProfileCommand(TARGET_ID, "operator-2", "OPERATOR"));

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT actor_id, actor_role, resource_type, resource_id, case_context, accessed_at"
                        + " FROM kyc_read_audit ORDER BY id");
        assertThat(rows).hasSize(2);
        assertThat(rows).extracting(row -> row.get("actor_id")).containsExactly("operator-1", "operator-2");
        assertThat(rows).allSatisfy(row -> {
            assertThat(row.get("actor_role")).isEqualTo("OPERATOR");
            assertThat(row.get("resource_type")).isEqualTo("KYC_PROFILE");
            assertThat(row.get("resource_id")).isEqualTo(TARGET_ID);
            assertThat(row.get("case_context")).isNull();
            assertThat(row.get("accessed_at")).isNotNull();
        });
    }

    @Test
    void unknownUser_leavesNoAuditRow() {
        assertThatThrownBy(() -> getKycProfileUseCase.execute(
                new GetKycProfileCommand("nobody", "operator-1", "OPERATOR")))
                .isInstanceOf(NoSuchElementException.class);

        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM kyc_read_audit", Long.class)).isZero();
    }
}
