package com.agricontract.user.application.usecase;

import com.agricontract.user.application.dto.EmailVerificationConfirmation;
import com.agricontract.user.application.dto.RegisterUserCommand;
import com.agricontract.user.application.dto.RegisterUserResult;
import com.agricontract.user.application.exception.VerificationEmailNotSentException;
import com.agricontract.user.application.port.VerificationEmailSender;
import com.agricontract.user.domain.exception.EmailAlreadyClaimedException;
import com.agricontract.user.domain.exception.EmailVerificationConflictException;
import com.agricontract.user.domain.model.vo.Role;
import com.agricontract.user.domain.model.vo.UserId;
import com.agricontract.user.domain.repository.UserProfileRepository;
import org.junit.jupiter.api.AfterEach;
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

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Email verification against real MySQL (V11), with the default policy (code valid 600 s, 5 wrong guesses, 60 s
 * resend cooldown): register sends the first code; the first user to confirm an address owns it; a wrong code is
 * counted and committed; parallel wrong guesses each cost an attempt; an unsent code is expired and does not hold
 * the cooldown.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers(disabledWithoutDocker = true)
class EmailVerificationIntegrationTest {

    private static final String SHARED = "shared@example.com";
    private static final int MAX_ATTEMPTS = 5;

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

    private ExecutorService executor;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM email_verification_challenge");
        jdbcTemplate.update("DELETE FROM user_profiles");
    }

    @AfterEach
    void tearDown() {
        if (executor != null) {
            executor.shutdownNow();
        }
    }

    @Test
    void register_sendsTheFirstCode_whichConfirmsTheAddress() {
        String code = registerAndCaptureCode("user-a", SHARED);

        assertThat(confirmUseCase.execute("user-a", code))
                .isInstanceOf(EmailVerificationConfirmation.Verified.class);
        assertThat(internalUserInfoUseCase.execute("user-a").emailVerified()).isTrue();
        assertThat(challengeCount("user-a")).isZero();
    }

    @Test
    void firstConfirmOwnsTheAddress_othersAreRefused() {
        String codeA = registerAndCaptureCode("user-a", SHARED);
        String codeB = registerAndCaptureCode("user-b", "SHARED@example.com");

        confirmUseCase.execute("user-a", codeA);

        assertThat(internalUserInfoUseCase.execute("user-a").email()).isEqualTo(SHARED);
        assertThatThrownBy(() -> confirmUseCase.execute("user-b", codeB))
                .isInstanceOf(EmailAlreadyClaimedException.class);
        assertThat(internalUserInfoUseCase.execute("user-b").email()).isNull();
        assertThat(internalUserInfoUseCase.execute("user-b").emailVerified()).isFalse();
        assertThatThrownBy(() -> register("user-c", SHARED)).isInstanceOf(EmailAlreadyClaimedException.class);
        assertThat(userProfileRepository.existsById(new UserId("user-c"))).isFalse();
    }

    @Test
    void registerAgain_afterVerification_returnsExistingProfile_withoutNewCode() {
        confirmUseCase.execute("user-a", registerAndCaptureCode("user-a", SHARED));
        clearInvocations(sender);

        RegisterUserResult again = register("user-a", "other@example.com");

        assertThat(again.isNew()).isFalse();
        assertThat(again.profile().email()).isEqualTo(SHARED);
        assertThat(again.profile().emailVerified()).isTrue();
        verify(sender, never()).send(any(), any(), any(), any(), any());
    }

    @Test
    void wrongCode_attemptIsCommitted() {
        String wrong = wrongCodeFor(registerAndCaptureCode("user-a", SHARED));

        assertThat(confirmUseCase.execute("user-a", wrong))
                .isEqualTo(new EmailVerificationConfirmation.Rejected(MAX_ATTEMPTS - 1));

        assertThat(attemptsLeft("user-a")).isEqualTo(MAX_ATTEMPTS - 1);
        assertThat(internalUserInfoUseCase.execute("user-a").email()).isNull();
    }

    @Test
    void parallelWrongCodes_eachCostAnAttempt() throws Exception {
        String code = registerAndCaptureCode("user-a", SHARED);
        String wrong = wrongCodeFor(code);
        int callers = MAX_ATTEMPTS * 2;
        executor = Executors.newFixedThreadPool(callers);
        CyclicBarrier start = new CyclicBarrier(callers);

        List<Future<String>> outcomes = new ArrayList<>();
        for (int i = 0; i < callers; i++) {
            outcomes.add(executor.submit(() -> {
                start.await(10, TimeUnit.SECONDS);
                try {
                    return confirmUseCase.execute("user-a", wrong) instanceof EmailVerificationConfirmation.Rejected
                            ? "REJECTED" : "VERIFIED";
                } catch (EmailVerificationConflictException e) {
                    return e.getReason();
                }
            }));
        }
        List<String> results = new ArrayList<>();
        for (Future<String> outcome : outcomes) {
            results.add(outcome.get(60, TimeUnit.SECONDS));
        }

        // Without the row lock every caller reads attempts_left = 5 and gets a free guess.
        assertThat(results).filteredOn("REJECTED"::equals).hasSize(MAX_ATTEMPTS);
        assertThat(results).filteredOn(EmailVerificationConflictException.ATTEMPTS_EXHAUSTED::equals)
                .hasSize(callers - MAX_ATTEMPTS);
        assertThat(attemptsLeft("user-a")).isZero();
        assertThatThrownBy(() -> confirmUseCase.execute("user-a", code))
                .isInstanceOfSatisfying(EmailVerificationConflictException.class, e -> assertThat(e.getReason())
                        .isEqualTo(EmailVerificationConflictException.ATTEMPTS_EXHAUSTED));
    }

    @Test
    void firstCodeNotSent_registrationStands_codeExpired_resendAllowedAtOnce() {
        doThrow(new VerificationEmailNotSentException("down", null))
                .when(sender).send(anyString(), any(), any(), any(), any());

        RegisterUserResult result = register("user-a", SHARED);

        assertThat(result.isNew()).isTrue();
        assertThat(result.verificationEmailSent()).isFalse();
        assertThat(userProfileRepository.existsById(new UserId("user-a"))).isTrue();
        Timestamp expiresAt = jdbcTemplate.queryForObject(
                "SELECT expires_at FROM email_verification_challenge WHERE user_id = 'user-a'", Timestamp.class);
        assertThat(expiresAt.toInstant()).isBeforeOrEqualTo(Instant.now());

        // Within the 60 s cooldown, but the unsent code is expired, so it does not count.
        doNothing().when(sender).send(anyString(), any(), any(), any(), any());
        String code = requestCode("user-a");
        assertThat(confirmUseCase.execute("user-a", code))
                .isInstanceOf(EmailVerificationConfirmation.Verified.class);
    }

    @Test
    void resendWithinCooldown_afterASentCode_refused() {
        registerAndCaptureCode("user-a", SHARED);

        assertThatThrownBy(() -> requestUseCase.execute("user-a"))
                .isInstanceOfSatisfying(EmailVerificationConflictException.class, e -> assertThat(e.getReason())
                        .isEqualTo(EmailVerificationConflictException.RESEND_TOO_SOON));
    }

    private RegisterUserResult register(String userId, String email) {
        return registerUserUseCase.execute(new RegisterUserCommand(userId, "HTX Ca Phe", Role.SELLER, email,
                null, null));
    }

    private String registerAndCaptureCode(String userId, String email) {
        clearInvocations(sender);
        assertThat(register(userId, email).verificationEmailSent()).isTrue();
        return capturedCode(userId);
    }

    private String requestCode(String userId) {
        clearInvocations(sender);
        requestUseCase.execute(userId);
        return capturedCode(userId);
    }

    private String capturedCode(String userId) {
        ArgumentCaptor<String> code = ArgumentCaptor.forClass(String.class);
        verify(sender).send(anyString(), eq(userId), anyString(), code.capture(), any());
        return code.getValue();
    }

    private static String wrongCodeFor(String code) {
        return code.equals("000000") ? "000001" : "000000";
    }

    private int attemptsLeft(String userId) {
        return jdbcTemplate.queryForObject(
                "SELECT attempts_left FROM email_verification_challenge WHERE user_id = ?", Integer.class, userId);
    }

    private int challengeCount(String userId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM email_verification_challenge WHERE user_id = ?", Integer.class, userId);
    }
}
