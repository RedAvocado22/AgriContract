package com.agricontract.user.infrastructure.keycloak;

import com.agricontract.user.application.dto.EmailVerificationConfirmation;
import com.agricontract.user.application.dto.RegisterUserCommand;
import com.agricontract.user.application.port.VerificationEmailSender;
import com.agricontract.user.application.usecase.ConfirmEmailVerificationUseCase;
import com.agricontract.user.application.usecase.PurgeUnverifiedAccountsUseCase;
import com.agricontract.user.application.usecase.RegisterUserUseCase;
import com.agricontract.user.domain.model.vo.Role;
import com.agricontract.user.domain.model.vo.UserId;
import com.agricontract.user.domain.repository.UserProfileRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.Transferable;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verify;

/**
 * D59 against the real realm file in a real Keycloak and real MySQL: the purge deletes only self-registered
 * accounts still unverified past the deadline, from Keycloak and user-service; it trusts user-service's record over
 * Keycloak's flag; a correct confirm sets the flag; the freed address can register again. The clock is moved
 * instead of waiting: Keycloak stamps accounts with the real time, the purge reads "now" from the test clock.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers(disabledWithoutDocker = true)
class UnverifiedAccountPurgeIntegrationTest {

    private static final String SECRET = "test-user-service-secret";
    private static final Path REALM = Path.of("../infra/keycloak/agricontract-realm.json");
    private static final Duration MAX_AGE = Duration.ofHours(24);

    @Container
    @ServiceConnection
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0");

    // Plain Keycloak: the registration-role provider (D47) is not needed here, since roles are mapped through the
    // admin API, so its listener is dropped from the realm before import.
    @Container
    static GenericContainer<?> keycloak = new GenericContainer<>("quay.io/keycloak/keycloak:24.0.4")
            .withEnv("KEYCLOAK_ADMIN", "admin")
            .withEnv("KEYCLOAK_ADMIN_PASSWORD", "admin")
            .withEnv("KEYCLOAK_USER_SERVICE_SECRET", SECRET)
            .withCopyToContainer(Transferable.of(realmWithoutCustomListener()),
                    "/opt/keycloak/data/import/agricontract-realm.json")
            .withCommand("start-dev", "--import-realm")
            .withExposedPorts(8080)
            .waitingFor(Wait.forHttp("/realms/agricontract").forPort(8080)
                    .withStartupTimeout(Duration.ofMinutes(3)));

    @DynamicPropertySource
    static void keycloakProperties(DynamicPropertyRegistry registry) {
        registry.add("keycloak.url", UnverifiedAccountPurgeIntegrationTest::keycloakUrl);
        registry.add("keycloak.client-secret", () -> SECRET);
        registry.add("user.unverified-account.max-age", MAX_AGE::toString);
    }

    @TestConfiguration
    static class MovableClockConfig {
        @Bean
        @Primary
        MovableClock movableClock() {
            return new MovableClock();
        }
    }

    @MockBean private VerificationEmailSender sender;

    @Autowired private PurgeUnverifiedAccountsUseCase purge;
    @Autowired private RegisterUserUseCase registerUseCase;
    @Autowired private ConfirmEmailVerificationUseCase confirmUseCase;
    @Autowired private UserProfileRepository profiles;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private MovableClock clock;

    private final RestClient admin = RestClient.create();

    @BeforeEach
    void setUp() {
        clock.reset();
        jdbcTemplate.update("DELETE FROM email_verification_challenge");
        jdbcTemplate.update("DELETE FROM user_profiles");
    }

    @Test
    void unverifiedPastTheDeadline_isDeletedEverywhere_andTheAddressIsFreeAgain() {
        String email = uniqueEmail();
        String userId = createAccount(email, "BUYER", false);
        register(userId, email);

        clock.advance(Duration.ofHours(25));
        purge.execute();

        assertThat(accountExists(userId)).isFalse();
        assertThat(profiles.existsById(new UserId(userId))).isFalse();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM email_verification_challenge WHERE user_id = ?", Integer.class, userId))
                .isZero();
        // Keycloak refuses a second account with the same email while the first exists.
        assertThat(createAccount(email, "BUYER", false)).isNotBlank();
    }

    @Test
    void unverifiedBeforeTheDeadline_isKept() {
        String email = uniqueEmail();
        String userId = createAccount(email, "SELLER", false);
        register(userId, email);

        clock.advance(Duration.ofHours(23));
        purge.execute();

        assertThat(accountExists(userId)).isTrue();
        assertThat(profiles.existsById(new UserId(userId))).isTrue();
    }

    @Test
    void abandonedBeforeTheProfileForm_isDeletedFromKeycloak() {
        String userId = createAccount(uniqueEmail(), "BUYER", false);

        clock.advance(Duration.ofHours(25));
        purge.execute();

        assertThat(accountExists(userId)).isFalse();
    }

    @Test
    void verifiedHere_butKeycloakMissedIt_isKept_andKeycloakIsToldAgain() {
        String email = uniqueEmail();
        String userId = createAccount(email, "SELLER", false);
        String code = registerAndCaptureCode(userId, email);
        assertThat(confirmUseCase.execute(userId, code)).isInstanceOf(EmailVerificationConfirmation.Verified.class);
        assertThat(emailVerifiedInKeycloak(userId)).isTrue();
        setEmailVerified(userId, false); // as if Keycloak was down at confirm time

        clock.advance(Duration.ofHours(25));
        purge.execute();

        assertThat(accountExists(userId)).isTrue();
        assertThat(profiles.existsById(new UserId(userId))).isTrue();
        assertThat(emailVerifiedInKeycloak(userId)).isTrue();
    }

    @Test
    void verifiedInKeycloak_isKept() {
        String userId = createAccount(uniqueEmail(), "BUYER", true);

        clock.advance(Duration.ofHours(25));
        purge.execute();

        assertThat(accountExists(userId)).isTrue();
    }

    @Test
    void staffAndSeedAccounts_areNeverTouched() {
        String operator = createAccount(uniqueEmail(), "OPERATOR", false);
        String inspector = createAccount(uniqueEmail(), "INSPECTOR", false);

        clock.advance(Duration.ofDays(30));
        purge.execute();

        assertThat(accountExists(operator)).isTrue();
        assertThat(accountExists(inspector)).isTrue();
        // Seed users from the realm file, among them the service account itself (unverified, no creation time).
        for (String seed : List.of("buyer1", "seller1", "seller2", "service-account-user-service")) {
            assertThat(findByUsername(seed)).as(seed).isNotNull();
        }
    }

    @Test
    void keycloakDown_atConfirm_stillVerifies() {
        // Paused, Keycloak accepts the connection but never answers: the confirm gives up after the read timeout.
        String email = uniqueEmail();
        String userId = createAccount(email, "BUYER", false);
        String code = registerAndCaptureCode(userId, email);
        keycloak.getDockerClient().pauseContainerCmd(keycloak.getContainerId()).exec();
        try {
            assertThat(confirmUseCase.execute(userId, code))
                    .isInstanceOf(EmailVerificationConfirmation.Verified.class);
        } finally {
            keycloak.getDockerClient().unpauseContainerCmd(keycloak.getContainerId()).exec();
        }
        assertThat(emailVerifiedInKeycloak(userId)).isFalse();

        clock.advance(Duration.ofHours(25));
        purge.execute();

        assertThat(accountExists(userId)).isTrue();
        assertThat(emailVerifiedInKeycloak(userId)).isTrue();
    }

    // ── user-service side ───────────────────────────────────────────

    private void register(String userId, String email) {
        registerUseCase.execute(new RegisterUserCommand(userId, "HTX Ca Phe", Role.SELLER, email, null, null));
    }

    private String registerAndCaptureCode(String userId, String email) {
        clearInvocations(sender);
        register(userId, email);
        ArgumentCaptor<String> code = ArgumentCaptor.forClass(String.class);
        verify(sender).send(anyString(), eq(userId), anyString(), code.capture(), any());
        return code.getValue();
    }

    // ── Keycloak side, through the master admin ─────────────────────

    private static String keycloakUrl() {
        return "http://" + keycloak.getHost() + ":" + keycloak.getMappedPort(8080);
    }

    private static String realmWithoutCustomListener() {
        try {
            ObjectMapper json = new ObjectMapper();
            ObjectNode realm = (ObjectNode) json.readTree(Files.readString(REALM));
            ArrayNode listeners = json.createArrayNode();
            realm.withArray("eventsListeners").forEach(listener -> {
                if (!listener.asText().equals("agricontract-registration-role")) {
                    listeners.add(listener);
                }
            });
            realm.set("eventsListeners", listeners);
            return json.writeValueAsString(realm);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read the realm file " + REALM.toAbsolutePath(), e);
        }
    }

    private String adminToken() {
        var form = new LinkedMultiValueMap<String, String>();
        form.add("grant_type", "password");
        form.add("client_id", "admin-cli");
        form.add("username", "admin");
        form.add("password", "admin");
        JsonNode token = admin.post().uri(keycloakUrl() + "/realms/master/protocol/openid-connect/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED).body(form).retrieve().body(JsonNode.class);
        return "Bearer " + token.get("access_token").asText();
    }

    private String usersUrl() {
        return keycloakUrl() + "/admin/realms/agricontract/users";
    }

    private String createAccount(String email, String role, boolean emailVerified) {
        String token = adminToken();
        var created = admin.post().uri(usersUrl()).header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("username", email, "email", email, "enabled", true, "emailVerified", emailVerified))
                .retrieve().toBodilessEntity();
        String location = created.getHeaders().getLocation().toString();
        String userId = location.substring(location.lastIndexOf('/') + 1);
        JsonNode roleRepresentation = admin.get().uri(keycloakUrl() + "/admin/realms/agricontract/roles/" + role)
                .header("Authorization", token).retrieve().body(JsonNode.class);
        admin.post().uri(usersUrl() + "/" + userId + "/role-mappings/realm").header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON).body(List.of(roleRepresentation))
                .retrieve().toBodilessEntity();
        return userId;
    }

    private boolean accountExists(String userId) {
        return admin.get().uri(usersUrl() + "/" + userId).header("Authorization", adminToken())
                .exchange((request, response) -> response.getStatusCode().is2xxSuccessful());
    }

    private boolean emailVerifiedInKeycloak(String userId) {
        return admin.get().uri(usersUrl() + "/" + userId).header("Authorization", adminToken())
                .retrieve().body(JsonNode.class).get("emailVerified").asBoolean();
    }

    private void setEmailVerified(String userId, boolean verified) {
        admin.put().uri(usersUrl() + "/" + userId).header("Authorization", adminToken())
                .contentType(MediaType.APPLICATION_JSON).body(Map.of("emailVerified", verified))
                .retrieve().toBodilessEntity();
    }

    private JsonNode findByUsername(String username) {
        JsonNode found = admin.get().uri(usersUrl() + "?exact=true&username=" + username)
                .header("Authorization", adminToken()).retrieve().body(JsonNode.class);
        return found.isEmpty() ? null : found.get(0);
    }

    private static String uniqueEmail() {
        return "u-" + UUID.randomUUID() + "@example.test";
    }

    static class MovableClock extends Clock {
        private Duration offset = Duration.ZERO;

        void advance(Duration by) {
            offset = offset.plus(by);
        }

        void reset() {
            offset = Duration.ZERO;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return Instant.now().plus(offset);
        }
    }
}
