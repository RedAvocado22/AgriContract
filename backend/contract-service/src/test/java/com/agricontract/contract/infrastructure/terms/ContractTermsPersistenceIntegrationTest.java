package com.agricontract.contract.infrastructure.terms;

import static com.agricontract.contract.domain.model.terms.CoffeeTermsFixtures.input;
import static com.agricontract.contract.domain.model.terms.CoffeeTermsFixtures.goods;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.agricontract.contract.domain.model.Contract;
import com.agricontract.contract.domain.model.terms.CanonicalTerms;
import com.agricontract.contract.domain.model.terms.ContractTemplateProfile;
import com.agricontract.contract.domain.model.terms.ContractTermsRules;
import com.agricontract.contract.domain.model.terms.ContractTermsSnapshot;
import com.agricontract.contract.domain.model.terms.TermsCanonicalizer;
import com.agricontract.contract.domain.model.vo.ContractId;
import com.agricontract.contract.domain.model.vo.ContractStatus;
import com.agricontract.contract.infrastructure.persistence.mapper.ContractMapper;
import com.agricontract.contract.infrastructure.persistence.repository.ContractRepositoryImpl;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Real MySQL + Flyway V6 + Hibernate validate: the stored text and fingerprint survive a round trip unchanged. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ImportAutoConfiguration(JacksonAutoConfiguration.class)
@Import({ContractRepositoryImpl.class, ContractMapper.class, JcsTermsCanonicalizer.class,
        ContractTermsConfiguration.class})
@Testcontainers
class ContractTermsPersistenceIntegrationTest {
    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0.42");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", () -> "root");
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }

    @Autowired ContractRepositoryImpl contracts;
    @Autowired TermsCanonicalizer canonicalizer;
    @Autowired ContractTermsRules rules;
    @Autowired ContractTemplateProfile template;
    @Autowired JdbcTemplate jdbc;

    @Test
    void coffeeTermsAndFingerprintRoundTripThroughMysqlUnchanged() {
        CanonicalTerms terms = CanonicalTerms.of(
                ContractTermsSnapshot.assemble(input(), goods(), template, rules), canonicalizer);
        ContractId id = new ContractId(UUID.randomUUID().toString());
        contracts.save(Contract.offerCoffee(id, goods().listingId().toString(), "buyer-1", "seller-1",
                "HTX Mua", "HTX Bán", "buyer@x.test", "seller@x.test", terms));

        String storedText = jdbc.queryForObject("SELECT terms_snapshot FROM contracts WHERE contract_id = ?",
                String.class, id.value());
        assertThat(storedText).isEqualTo(terms.canonicalJson());

        Contract loaded = contracts.findById(id).orElseThrow();
        assertThat(loaded.getStatus()).isEqualTo(ContractStatus.OFFERED);
        assertThat(loaded.getCoffeeTerms().signedContentHash()).isEqualTo(terms.signedContentHash());
        assertThat(loaded.getTerms().quantity().value()).isEqualByComparingTo("2500.5");
        assertThat(loaded.getProductName()).isEqualTo("Robusta Đắk Lắk loại 1");
    }

    @Test
    void templateFingerprintComesFromTheTemplateFile() {
        assertThat(template.contractTemplateCode()).isEqualTo("VN_COFFEE_B2B");
        assertThat(template.contractTemplateVersion()).isEqualTo("1.0");
        assertThat(template.contractTemplateHash()).matches("^[a-f0-9]{64}$");
    }

    @Test
    void databaseRefusesAFingerprintWithoutTerms() {
        assertThatThrownBy(() -> jdbc.update("INSERT INTO contracts (contract_id, listing_id, seller_id, buyer_id,"
                + " product_name, buyer_org_name, seller_org_name, buyer_email, seller_email, quantity, quantity_unit,"
                + " agreed_price, currency, delivery_deadline, status, signed_content_hash) VALUES ('c-x', 'l', 's',"
                + " 'b', 'p', 'B', 'S', 'b@x.test', 's@x.test', 1, 'KG', 1.00, 'VND', '2026-12-31', 'OFFERED', ?)",
                "a".repeat(64)))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("chk_contracts_terms_hash_pair");
    }
}
