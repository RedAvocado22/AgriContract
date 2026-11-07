package com.agricontract.contract.infrastructure.consent;

import static com.agricontract.contract.domain.model.terms.CoffeeTermsFixtures.goods;
import static com.agricontract.contract.domain.model.terms.CoffeeTermsFixtures.input;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.agricontract.contract.application.service.SigningConsentRecorder;
import com.agricontract.contract.domain.exception.ContractConflictException;
import com.agricontract.contract.domain.model.Contract;
import com.agricontract.contract.domain.model.consent.ConsentEvidence;
import com.agricontract.contract.domain.model.consent.ConsentStatements;
import com.agricontract.contract.domain.model.consent.SignerRole;
import com.agricontract.contract.domain.model.terms.CanonicalTerms;
import com.agricontract.contract.domain.model.terms.ContractTemplateProfile;
import com.agricontract.contract.domain.model.terms.ContractTermsRules;
import com.agricontract.contract.domain.model.terms.ContractTermsSnapshot;
import com.agricontract.contract.domain.model.terms.TermsCanonicalizer;
import com.agricontract.contract.domain.model.vo.ContractId;
import com.agricontract.contract.infrastructure.persistence.consent.JdbcConsentEvidenceRepository;
import com.agricontract.contract.infrastructure.terms.ContractTermsConfiguration;
import com.agricontract.contract.infrastructure.terms.JcsTermsCanonicalizer;
import java.util.Map;
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
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Real MySQL: the statement files load, consent rows are written, and the table refuses UPDATE/DELETE. */
@DataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ImportAutoConfiguration(JacksonAutoConfiguration.class)
@Import({SigningConsentRecorder.class, JdbcConsentEvidenceRepository.class, ConsentConfiguration.class,
        JcsTermsCanonicalizer.class, ContractTermsConfiguration.class})
@Testcontainers
class SigningConsentPersistenceIntegrationTest {
    private static final String BUYER = "buyer-1";
    private static final String SELLER = "seller-1";

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0.42");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", () -> "root");
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }

    @Autowired SigningConsentRecorder recorder;
    @Autowired ConsentStatements statements;
    @Autowired TermsCanonicalizer canonicalizer;
    @Autowired ContractTermsRules rules;
    @Autowired ContractTemplateProfile template;
    @Autowired TransactionTemplate tx;
    @Autowired JdbcTemplate jdbc;

    @Test
    void statementFilesLoadWithTheHashOfTheirText() throws Exception {
        for (SignerRole role : SignerRole.values()) {
            var s = statements.activeSignContract(role);
            assertThat(s.statementVersion()).isEqualTo("1.0");
            assertThat(s.text()).startsWith("Tôi xác nhận").doesNotEndWith("\n");
            assertThat(s.statementHash()).isEqualTo(CanonicalTerms.sha256(s.text()));
        }
        assertThat(statements.activeSignContract(SignerRole.BUYER).text()).contains("bên mua");
        assertThat(statements.activeSignContract(SignerRole.SELLER).text()).contains("bên bán");
    }

    @Test
    void consentIsStoredAsShown() {
        Contract contract = offered();
        String hash = contract.getCoffeeTerms().signedContentHash();

        ConsentEvidence e = tx.execute(s -> recorder.record(contract, SELLER, SignerRole.SELLER, "1.0", hash, "corr-9"));

        Map<String, Object> row = jdbc.queryForMap("SELECT * FROM consent_evidence WHERE consent_id = ?",
                e.consentId().toString());
        assertThat(row).containsEntry("action_type", "SIGN_CONTRACT")
                .containsEntry("subject_type", "CONTRACT")
                .containsEntry("subject_id", contract.getContractId().value())
                .containsEntry("actor_user_id", SELLER)
                .containsEntry("actor_role", "SELLER")
                .containsEntry("statement_code", "SIGN_CONTRACT_AS_SELLER")
                .containsEntry("statement_version", "1.0")
                .containsEntry("statement_hash", statements.activeSignContract(SignerRole.SELLER).statementHash())
                .containsEntry("presented_content_hash", hash)
                .containsEntry("correlation_id", "corr-9");
        assertThat(row.get("consented_at")).isNotNull();
    }

    @Test
    void refusedConsentWritesNothing() {
        Contract contract = offered();

        assertThatThrownBy(() -> tx.execute(s -> recorder.record(contract, SELLER, SignerRole.SELLER, "1.0",
                "f".repeat(64), null))).isInstanceOf(ContractConflictException.class);

        assertThat(count(contract)).isZero();
    }

    @Test
    void eachAttemptIsItsOwnRow() {
        Contract contract = offered();
        String hash = contract.getCoffeeTerms().signedContentHash();

        tx.execute(s -> recorder.record(contract, BUYER, SignerRole.BUYER, "1.0", hash, null));
        tx.execute(s -> recorder.record(contract, BUYER, SignerRole.BUYER, "1.0", hash, null));

        assertThat(count(contract)).isEqualTo(2);
    }

    @Test
    void rowsCannotBeUpdatedOrDeleted() {
        Contract contract = offered();
        String hash = contract.getCoffeeTerms().signedContentHash();
        String id = tx.execute(s -> recorder.record(contract, BUYER, SignerRole.BUYER, "1.0", hash, null))
                .consentId().toString();

        assertThatThrownBy(() -> jdbc.update("UPDATE consent_evidence SET presented_content_hash = ? WHERE consent_id = ?",
                "e".repeat(64), id)).isInstanceOf(DataAccessException.class).hasMessageContaining("append-only");
        assertThatThrownBy(() -> jdbc.update("DELETE FROM consent_evidence WHERE consent_id = ?", id))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("append-only");
        assertThat(jdbc.queryForObject("SELECT presented_content_hash FROM consent_evidence WHERE consent_id = ?",
                String.class, id)).isEqualTo(hash);
    }

    @Test
    void recordingOutsideATransactionIsRefused() {
        Contract contract = offered();

        assertThatThrownBy(() -> recorder.record(contract, BUYER, SignerRole.BUYER, "1.0",
                contract.getCoffeeTerms().signedContentHash(), null))
                .isInstanceOf(IllegalTransactionStateException.class);
    }

    private Contract offered() {
        CanonicalTerms terms = CanonicalTerms.of(ContractTermsSnapshot.assemble(input(), goods(), template, rules),
                canonicalizer);
        return Contract.offerCoffee(new ContractId(UUID.randomUUID().toString()), goods().listingId().toString(),
                BUYER, SELLER, "HTX Mua", "HTX Bán", "b@x.test", "s@x.test", terms);
    }

    private int count(Contract contract) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM consent_evidence WHERE subject_id = ?", Integer.class,
                contract.getContractId().value());
    }
}
