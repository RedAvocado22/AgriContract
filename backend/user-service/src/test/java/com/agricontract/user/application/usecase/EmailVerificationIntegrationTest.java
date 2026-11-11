package com.agricontract.user.application.usecase;

import com.agricontract.user.application.dto.RegisterUserCommand;
import com.agricontract.user.application.port.VerificationEmailSender;
import com.agricontract.user.domain.exception.EmailAlreadyClaimedException;
import com.agricontract.user.domain.exception.InvalidVerificationCodeException;
import com.agricontract.user.domain.model.vo.Role;
import com.agricontract.user.domain.model.vo.UserId;
import com.agricontract.user.domain.repository.UserProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verify;

/**
 * Register email ownership against real MySQL (V11): two users may register the same unproven address, the first to
 * confirm a code owns it, the second confirm and any later register of that address get EMAIL_ALREADY_CLAIMED, and a
 * wrong code is counted even though the request fails.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers(disabledWithoutDocker = true)
class EmailVerificationIntegrationTest {

    private static final String SHARED = "shared@example.com";

    @Container
    @ServiceConnection
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0");

    @MockBean private VerificationEmailSender sender;

    @Autowired private RegisterUserUseCase registerUserUseCase;
    @Autowired private RequestEmailVerificationUseCase requestUseCase;
    @Autowired private ConfirmEmailVerificationUseCase confirmUseCase;
    @Autowired private GetInternalUserInfoUseCase internalUserInfoUseCase;
    @Autowired private UserProfileRepository userProfileRepository;
    @Autowired private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM email_verification_challenge");
        jdbcTemplate.update("DELETE FROM user_profiles");
    }

    @Test
    void firstConfirmOwnsTheAddress_othersAreRefused() {
        register("user-a", SHARED);
        register("user-b", "SHARED@example.com");
        String codeA = requestCode("user-a");
        String codeB = requestCode("user-b");

        confirmUseCase.execute("user-a", codeA);

        assertThat(internalUserInfoUseCase.execute("user-a").email()).isEqualTo(SHARED);
        assertThatThrownBy(() -> confirmUseCase.execute("user-b", codeB))
                .isInstanceOf(EmailAlreadyClaimedException.class);
        assertThat(internalUserInfoUseCase.execute("user-b").email()).isNull();
        assertThatThrownBy(() -> register("user-c", SHARED)).isInstanceOf(EmailAlreadyClaimedException.class);
        assertThat(userProfileRepository.existsById(new UserId("user-c"))).isFalse();
    }

    @Test
    void registerAgain_afterVerification_returnsExistingProfile() {
        register("user-a", SHARED);
        confirmUseCase.execute("user-a", requestCode("user-a"));

        var again = registerUserUseCase.execute(command("user-a", "other@example.com"));

        assertThat(again.isNew()).isFalse();
        assertThat(again.profile().email()).isEqualTo(SHARED);
        assertThat(again.profile().emailVerified()).isTrue();
    }

    @Test
    void wrongCode_attemptIsCommitted() {
        register("user-a", SHARED);
        String code = requestCode("user-a");
        String wrong = code.equals("000000") ? "000001" : "000000";

        assertThatThrownBy(() -> confirmUseCase.execute("user-a", wrong))
                .isInstanceOf(InvalidVerificationCodeException.class);

        assertThat(jdbcTemplate.queryForObject(
                "SELECT attempts_left FROM email_verification_challenge WHERE user_id = 'user-a'", Integer.class))
                .isEqualTo(4);
        assertThat(internalUserInfoUseCase.execute("user-a").email()).isNull();
    }

    private void register(String userId, String email) {
        registerUserUseCase.execute(command(userId, email));
    }

    private static RegisterUserCommand command(String userId, String email) {
        return new RegisterUserCommand(userId, "HTX Ca Phe", Role.SELLER, email, null, null);
    }

    private String requestCode(String userId) {
        clearInvocations(sender);
        requestUseCase.execute(userId);
        ArgumentCaptor<String> code = ArgumentCaptor.forClass(String.class);
        verify(sender).send(anyString(), eq(userId), anyString(), code.capture(), any());
        return code.getValue();
    }
}
