package com.agricontract.contract.application.usecase;

import static com.agricontract.contract.domain.model.terms.CoffeeTermsFixtures.goods;
import static com.agricontract.contract.domain.model.terms.CoffeeTermsFixtures.input;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.agricontract.contract.application.dto.CreateContractCommand;
import com.agricontract.contract.application.dto.ListingReservation;
import com.agricontract.contract.application.dto.ListingResponse;
import com.agricontract.contract.application.dto.UserInfo;
import com.agricontract.contract.application.port.ListingPort;
import com.agricontract.contract.application.port.UserPort;
import com.agricontract.contract.application.service.ReservationCompensation;
import com.agricontract.contract.domain.exception.ContractConflictException;
import com.agricontract.contract.infrastructure.consent.ConsentConfiguration;
import com.agricontract.contract.infrastructure.contract.FrozenSchemas;
import com.agricontract.contract.infrastructure.messaging.OutboxContractEventPublisher;
import com.agricontract.contract.infrastructure.persistence.idempotency.JdbcIdempotencyStore;
import com.agricontract.contract.infrastructure.persistence.mapper.ContractMapper;
import com.agricontract.contract.infrastructure.persistence.repository.ContractRepositoryImpl;
import com.agricontract.contract.infrastructure.persistence.signature.JdbcSignatureRepository;
import com.agricontract.contract.infrastructure.terms.ContractTermsConfiguration;
import com.agricontract.contract.infrastructure.terms.JcsTermsCanonicalizer;
import com.agricontract.eventing.EventingAutoConfiguration;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * #232 on real MySQL with product-service and user-service stubbed: the OFFERED row, its reservedQuantity and
 * contract.created commit together, once per Idempotency-Key; a failed local commit leaves none of them and gives
 * the reservation back.
 */
@DataJpaTest(properties = {"agricontract.eventing.outbox.enabled=false",
        "contract.create.compensation-backoff-ms=0"})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ImportAutoConfiguration({JacksonAutoConfiguration.class, EventingAutoConfiguration.class})
@Import({CreateContractUseCase.class, ReservationCompensation.class, OutboxContractEventPublisher.class,
        JdbcIdempotencyStore.class, ContractRepositoryImpl.class, JdbcSignatureRepository.class, ContractMapper.class,
        JcsTermsCanonicalizer.class, ContractTermsConfiguration.class, ConsentConfiguration.class})
@Testcontainers
class CreateContractIntegrationTest {
    private static final String BUYER = "0b7e3b7a-1c1e-4c5e-9f0a-3d2b1a0c9e8f";
    private static final String SELLER = "5a4c3b2a-1f0e-4d9c-8b7a-6f5e4d3c2b1a";
    private static final String LISTING = goods().listingId().toString();
    private static final String TOKEN = "2026-11-10T06:00:00.123456Z";
    private static final BigDecimal TOTAL = new BigDecimal("2500.5");

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0.42");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", () -> "root");
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }

    @MockBean ListingPort listings;
    @MockBean UserPort users;
    @Autowired CreateContractUseCase useCase;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void stubs() {
        reset(listings, users);
        when(listings.getListing(LISTING)).thenReturn(new ListingResponse(LISTING, SELLER,
                goods().productId().toString(), "Robusta", new BigDecimal("10000"), "KG", new BigDecimal("90000"),
                "VND", LocalDate.of(2027, 2, 1), "AVAILABLE"));
        when(users.findUser(BUYER)).thenReturn(Optional.of(user(BUYER, "HTX Mua")));
        when(users.findUser(SELLER)).thenReturn(Optional.of(user(SELLER, "HTX Bán")));
        when(listings.contractSnapshot(LISTING, TOKEN)).thenReturn(goods());
        when(listings.reserve(anyString(), eq(LISTING), any(), eq(TOKEN))).thenAnswer(call -> new ListingReservation(
                call.getArgument(0), LISTING, call.getArgument(2), "RESERVED", new BigDecimal("7499.5")));
    }

    @Test
    void offerRow_reservedQuantity_andContractCreated_commitTogether() throws Exception {
        String id = useCase.execute(command(UUID.randomUUID())).contract().contractId();

        assertThat(jdbc.queryForObject("SELECT status FROM contracts WHERE contract_id = ?", String.class, id))
                .isEqualTo("OFFERED");
        assertThat(jdbc.queryForObject("SELECT reserved_quantity FROM contracts WHERE contract_id = ?",
                BigDecimal.class, id)).isEqualByComparingTo(TOTAL);
        List<JsonNode> created = createdEvents(id);
        assertThat(created).hasSize(1);
        FrozenSchemas.assertEvent("contractCreated", created.get(0));
        assertThat(created.get(0).at("/payload/reservedQuantity").decimalValue()).isEqualByComparingTo(TOTAL);
        assertThat(created.get(0).at("/payload/listingId").asText()).isEqualTo(LISTING);
        verify(listings).reserve(id, LISTING, TOTAL, TOKEN);
        verify(listings, never()).releaseReservation(anyString(), anyString(), any());
    }

    @Test
    void retryWithTheSameKey_reservesOnce_storesOnce_publishesOnce() {
        UUID key = UUID.randomUUID();
        String first = useCase.execute(command(key)).contract().contractId();

        CreateContractUseCase.Result retry = useCase.execute(command(key));

        assertThat(retry.replayed()).isTrue();
        assertThat(retry.contract().contractId()).isEqualTo(first);
        verify(listings, times(1)).reserve(anyString(), anyString(), any(), anyString());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM contracts WHERE buyer_id = ? AND contract_id = ?",
                Integer.class, BUYER, first)).isEqualTo(1);
        assertThat(createdEvents(first)).hasSize(1);
    }

    @Test
    void localCommitFails_noOfferNoEventNoKey_andTheReservationIsReleased() {
        // contracts.buyer_org_name is VARCHAR(255): the insert fails inside the create transaction.
        when(users.findUser(BUYER)).thenReturn(Optional.of(user(BUYER, "x".repeat(300))));
        UUID key = UUID.randomUUID();

        assertThatThrownBy(() -> useCase.execute(command(key))).isInstanceOf(RuntimeException.class);

        verify(listings).releaseReservation(anyString(), eq(LISTING), eq(TOTAL));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM contracts WHERE buyer_id = ?", Integer.class, BUYER)
                + jdbc.queryForObject("SELECT COUNT(*) FROM idempotency_record WHERE idempotency_key = ?",
                Integer.class, key.toString())).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM event_outbox WHERE event_type = 'contract.created'"
                + " AND aggregate_id NOT IN (SELECT contract_id FROM contracts)", Integer.class)).isZero();
    }

    @Test
    void insufficientQuantity_leavesNoOfferedRow() {
        when(listings.reserve(anyString(), eq(LISTING), any(), eq(TOKEN))).thenThrow(
                new ContractConflictException("INSUFFICIENT_LISTING_QUANTITY", "Only 100 kg left"));
        UUID key = UUID.randomUUID();

        assertThatThrownBy(() -> useCase.execute(command(key))).isInstanceOf(ContractConflictException.class);

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM idempotency_record WHERE idempotency_key = ?",
                Integer.class, key.toString())).isZero();
    }

    private static CreateContractCommand command(UUID key) {
        return new CreateContractCommand(BUYER, key, LISTING, TOKEN, input());
    }

    private static UserInfo user(String id, String org) {
        return new UserInfo(id, org, id.substring(0, 4) + "@example.com", "BUYER", "VERIFIED", null, null);
    }

    private List<JsonNode> createdEvents(String contractId) {
        return jdbc.queryForList("SELECT envelope FROM event_outbox WHERE event_type = 'contract.created'"
                        + " AND aggregate_id = ?", String.class, contractId).stream()
                .map(json -> {
                    try {
                        return new ObjectMapper().readTree(json);
                    } catch (Exception e) {
                        throw new IllegalStateException(e);
                    }
                }).toList();
    }
}
