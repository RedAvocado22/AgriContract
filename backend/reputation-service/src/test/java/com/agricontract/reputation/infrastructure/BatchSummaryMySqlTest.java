package com.agricontract.reputation.infrastructure;

import com.agricontract.reputation.domain.model.CompletedContractFact;
import com.agricontract.reputation.domain.repository.FactRepository;
import com.agricontract.reputation.infrastructure.contract.FrozenSchemas;
import com.agricontract.reputation.infrastructure.persistence.JdbcReputationReadRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {"service.internal-secret=batch-test-secret", "gateway.internal-secret=gateway-test-secret",
        "spring.rabbitmq.username=unused", "spring.rabbitmq.password=unused",
        "spring.rabbitmq.listener.simple.auto-startup=false", "reputation.dlq-monitor.enabled=false"})
@AutoConfigureMockMvc
class BatchSummaryMySqlTest {
    private static final Instant NOW = ReputationMySqlTest.NOW;
    private static final String PATH = "/internal/v1/reputation/public-summaries";
    @Autowired private FactRepository facts;
    @Autowired private JdbcTemplate runtime;
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        ReputationDatabaseFixture.properties(registry);
    }

    @TestConfiguration
    static class TimeConfiguration {
        @Bean
        @Primary
        Clock testClock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }
    }

    @BeforeEach
    void clearFixtures() {
        var admin = ReputationDatabaseFixture.ADMIN;
        admin.update("DELETE FROM lock_override_event");
        admin.update("DELETE FROM lock_entry");
        admin.update("DELETE FROM dispute_outcome_fact");
        admin.update("DELETE FROM completed_contract_fact");
    }

    @Test
    void mixedBatchMatchesPublicSummaryAndFrozenSchemaForEveryState() throws Exception {
        List<UUID> ids = IntStream.range(0, 8).mapToObj(i -> UUID.randomUUID()).toList();
        UUID unknown = ids.get(0), completedOnly = ids.get(1), locksOnly = ids.get(2),
                expired = ids.get(3), early = ids.get(4), multiple = ids.get(5), mixed = ids.get(6), half = ids.get(7);
        complete(completedOnly, UUID.randomUUID()); // buyer
        complete(UUID.randomUUID(), completedOnly); // seller
        complete(completedOnly, completedOnly); // same row counts once
        lock(locksOnly, NOW.plusSeconds(100));
        complete(expired, UUID.randomUUID());
        lock(expired, NOW); // exact expiry
        complete(early, UUID.randomUUID());
        unlock(lock(early, NOW.plusSeconds(100)));
        unlock(lock(multiple, NOW.plusSeconds(100)));
        UUID remaining = lock(multiple, NOW.plusSeconds(200));
        complete(mixed, UUID.randomUUID());
        complete(UUID.randomUUID(), mixed);
        lock(mixed, NOW.minusSeconds(10));
        lock(mixed, NOW.plusSeconds(100));
        complete(half, UUID.randomUUID());
        for (int i = 0; i < 7; i++) {
            lock(half, NOW.minusSeconds(10));
        }
        // Unrequested users must not leak into a batch or affect another user's aggregates.
        complete(UUID.randomUUID(), UUID.randomUUID());
        lock(UUID.randomUUID(), NOW.plusSeconds(100));
        Map<UUID, JsonNode> items = batch(ids);
        assertItem(items, unknown, null, false);
        assertItem(items, completedOnly, 100, false);
        assertItem(items, locksOnly, 0, true);
        assertItem(items, expired, 50, false);
        assertItem(items, early, 50, false);
        assertItem(items, multiple, 0, true);
        assertItem(items, mixed, 50, true);
        assertItem(items, half, 13, false); // 12.5 rounds HALF_UP
        for (UUID id : ids) {
            var result = mvc.perform(get(ReputationMySqlTest.route(id))
                    .header("X-Gateway-Secret", "gateway-test-secret")
                    .header("X-User-Id", UUID.randomUUID()).header("X-User-Role", "BUYER"))
                    .andExpect(status().isOk()).andReturn();
            JsonNode publicBody = json.readTree(result.getResponse().getContentAsString());
            FrozenSchemas.assertResponse("ReputationPublicSummary", publicBody);
            assertThat(publicBody.get("score")).isEqualTo(items.get(id).get("score"));
            assertThat(publicBody.get("locked")).isEqualTo(items.get(id).get("locked"));
            assertThat(publicBody.has("lockHistory")).isTrue();
            assertThat(items.get(id).properties()).extracting(Map.Entry::getKey)
                    .containsExactlyInAnyOrder("userId", "score", "locked");
        }
        unlock(remaining);
        assertItem(batch(List.of(multiple)), multiple, 0, false);
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 100})
    void oneAggregateSqlExecutionRegardlessOfBatchSize(int size) {
        var ids = IntStream.range(0, size).mapToObj(i -> UUID.randomUUID()).toList();
        complete(ids.getFirst(), UUID.randomUUID());
        var countedJdbc = spy(new JdbcTemplate(runtime.getDataSource()));
        var repository = new JdbcReputationReadRepository(countedJdbc);
        var results = repository.publicSummaries(ids, NOW);
        assertThat(results).hasSize(size);
        assertThat(results.getFirst().score()).isEqualByComparingTo("100");
        verify(countedJdbc, times(1)).query(any(PreparedStatementCreator.class), any(ResultSetExtractor.class));
    }

    @Test
    void maximumBatchReturnsEveryRequestedIdExactlyOnce() throws Exception {
        var ids = new ArrayList<>(IntStream.range(0, 100).mapToObj(i -> UUID.randomUUID()).toList());
        complete(ids.getFirst(), UUID.randomUUID());
        lock(ids.get(99), NOW.plusSeconds(100));
        var items = batch(ids);
        assertItem(items, ids.getFirst(), 100, false);
        assertItem(items, ids.get(50), null, false);
        assertItem(items, ids.get(99), 0, true);
    }

    @Test
    void subsecondBoundaryMatchesHistoryWithoutRoundingClockUp() {
        UUID id = UUID.randomUUID();
        Instant until = NOW.plusSeconds(1);
        lock(id, until);
        var repository = new JdbcReputationReadRepository(runtime);
        assertThat(repository.publicSummaries(List.of(id), until.minusNanos(1)).getFirst().locked()).isTrue();
        assertThat(repository.lockHistory(id, until.minusNanos(1)).getFirst().status().name()).isEqualTo("ACTIVE");
        assertThat(repository.publicSummaries(List.of(id), until).getFirst().locked()).isFalse();
        assertThat(repository.lockHistory(id, until).getFirst().status().name()).isEqualTo("EXPIRED");
    }

    @Test
    void internalSecretDoesNotAuthenticatePublicSummaryAndIdentityDoesNotLeak() throws Exception {
        batch(List.of(UUID.randomUUID()));
        mvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("userIds", List.of(UUID.randomUUID())))))
                .andExpect(status().isUnauthorized());
        mvc.perform(get(ReputationMySqlTest.route(UUID.randomUUID())).header("X-Internal-Secret", "batch-test-secret"))
                .andExpect(status().isUnauthorized());
    }

    private Map<UUID, JsonNode> batch(List<UUID> ids) throws Exception {
        var result = mvc.perform(post(PATH).header("X-Internal-Secret", "batch-test-secret")
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("userIds", ids))))
                .andExpect(status().isOk()).andReturn();
        JsonNode body = json.readTree(result.getResponse().getContentAsString());
        FrozenSchemas.assertResponse("ReputationBatchSummaryResponse", body);
        assertThat(body.path("items")).hasSize(ids.size());
        Map<UUID, JsonNode> items = new HashMap<>();
        for (JsonNode item : body.path("items")) {
            assertThat(items.put(UUID.fromString(item.path("userId").asText()), item)).isNull();
        }
        assertThat(items.keySet()).containsExactlyInAnyOrderElementsOf(ids);
        return items;
    }

    private static void assertItem(Map<UUID, JsonNode> items, UUID id, Integer score, boolean locked) {
        if (score == null) {
            assertThat(items.get(id).get("score").isNull()).isTrue();
        } else {
            assertThat(items.get(id).get("score").intValue()).isEqualTo(score);
        }
        assertThat(items.get(id).get("locked").booleanValue()).isEqualTo(locked);
    }

    private void complete(UUID buyer, UUID seller) {
        facts.append(new CompletedContractFact(UUID.randomUUID(), buyer, seller, NOW, UUID.randomUUID()));
    }

    private static UUID lock(UUID user, Instant until) {
        return ReputationMySqlTest.lock(user, NOW.minusSeconds(1000), until);
    }

    private static void unlock(UUID entry) {
        ReputationMySqlTest.override(entry, UUID.randomUUID(), UUID.randomUUID());
    }
}
