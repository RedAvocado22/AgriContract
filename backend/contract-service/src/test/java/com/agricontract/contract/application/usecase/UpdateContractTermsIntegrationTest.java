package com.agricontract.contract.application.usecase;

import static com.agricontract.contract.domain.model.terms.CoffeeTermsFixtures.goods;
import static com.agricontract.contract.domain.model.terms.CoffeeTermsFixtures.input;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.agricontract.contract.application.dto.CoffeeContractView;
import com.agricontract.contract.application.dto.UpdateContractTermsCommand;
import com.agricontract.contract.application.exception.IdempotencyKeyReusedException;
import com.agricontract.contract.domain.exception.ContractConflictException;
import com.agricontract.contract.domain.model.Contract;
import com.agricontract.contract.domain.model.terms.CanonicalTerms;
import com.agricontract.contract.domain.model.terms.ContractTemplateProfile;
import com.agricontract.contract.domain.model.terms.ContractTermsInput;
import com.agricontract.contract.domain.model.terms.ContractTermsRules;
import com.agricontract.contract.domain.model.terms.ContractTermsSnapshot;
import com.agricontract.contract.domain.model.terms.InvalidContractTermsException;
import com.agricontract.contract.domain.model.terms.TermsCanonicalizer;
import com.agricontract.contract.domain.model.terms.VndAmount;
import com.agricontract.contract.domain.model.vo.ContractId;
import com.agricontract.contract.domain.model.vo.ContractStatus;
import com.agricontract.contract.infrastructure.persistence.idempotency.JdbcIdempotencyStore;
import com.agricontract.contract.infrastructure.persistence.mapper.ContractMapper;
import com.agricontract.contract.infrastructure.persistence.repository.ContractRepositoryImpl;
import com.agricontract.contract.infrastructure.persistence.signature.JdbcSignatureRepository;
import com.agricontract.contract.infrastructure.terms.ContractTermsConfiguration;
import com.agricontract.contract.infrastructure.terms.JcsTermsCanonicalizer;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Real MySQL, real transactions (each command commits): idempotency, freeze and concurrent writers. */
@DataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ImportAutoConfiguration(JacksonAutoConfiguration.class)
@Import({UpdateContractTermsUseCase.class, JdbcIdempotencyStore.class, ContractRepositoryImpl.class, JdbcSignatureRepository.class,
        ContractMapper.class, JcsTermsCanonicalizer.class, ContractTermsConfiguration.class})
@Testcontainers
class UpdateContractTermsIntegrationTest {
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

    @Autowired UpdateContractTermsUseCase useCase;
    @Autowired ContractRepositoryImpl contracts;
    @Autowired TermsCanonicalizer canonicalizer;
    @Autowired ContractTermsRules rules;
    @Autowired ContractTemplateProfile template;
    @Autowired JdbcTemplate jdbc;

    @Test
    void revisionIsStoredWithANewFingerprint() {
        String id = offer();
        String before = hashOf(id);

        CoffeeContractView view = useCase.execute(command(id, SELLER, UUID.randomUUID(), withPrice("96000.00")));

        assertThat(view.status()).isEqualTo(ContractStatus.NEGOTIATING);
        assertThat(view.signedContentHash()).isNotEqualTo(before).isEqualTo(hashOf(id));
        assertThat(view.termsJson()).contains("\"agreedPrice\":\"96000.00\"");
        assertThat(view.reservedQuantity()).isEqualByComparingTo("2500.5");
    }

    /** The lost-response case: the retry must not undo the other party's later change. */
    @Test
    void retryWithTheSameKeyDoesNotApplyTheChangeAgain() {
        String id = offer();
        UUID sellerKey = UUID.randomUUID();
        useCase.execute(command(id, SELLER, sellerKey, withPrice("96000.00")));
        useCase.execute(command(id, BUYER, UUID.randomUUID(), withPrice("95000.00")));

        CoffeeContractView replay = useCase.execute(command(id, SELLER, sellerKey, withPrice("96000.00")));

        assertThat(replay.termsJson()).contains("\"agreedPrice\":\"95000.00\"");
        assertThat(jdbc.queryForObject("SELECT agreed_price FROM contracts WHERE contract_id = ?", String.class, id))
                .isEqualTo("95000.00");
    }

    @Test
    void sameKeyForADifferentRequestIsRefused() {
        String id = offer();
        UUID key = UUID.randomUUID();
        useCase.execute(command(id, SELLER, key, withPrice("96000.00")));

        assertThatThrownBy(() -> useCase.execute(command(id, SELLER, key, withPrice("97000.00"))))
                .isInstanceOf(IdempotencyKeyReusedException.class);
    }

    @Test
    void keysAreScopedPerActor() {
        String id = offer();
        UUID shared = UUID.randomUUID();
        useCase.execute(command(id, SELLER, shared, withPrice("96000.00")));

        CoffeeContractView buyerView = useCase.execute(command(id, BUYER, shared, withPrice("97000.00")));
        assertThat(buyerView.termsJson()).contains("\"agreedPrice\":\"97000.00\"");
    }

    @Test
    void aRejectedRequestDoesNotConsumeTheKey() {
        String id = offer();
        UUID key = UUID.randomUUID();

        assertThatThrownBy(() -> useCase.execute(command(id, SELLER, key, withPrice("120000.00"))))
                .isInstanceOf(InvalidContractTermsException.class)
                .hasMessageContaining("priceBand");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM idempotency_record WHERE idempotency_key = ?",
                Integer.class, key.toString())).isZero();

        CoffeeContractView fixed = useCase.execute(command(id, SELLER, key, withPrice("96000.00")));
        assertThat(fixed.termsJson()).contains("\"agreedPrice\":\"96000.00\"");
    }

    @Test
    void termsAreFrozenAfterTheFirstSignature() {
        String id = offer();
        signAsBuyer(id);

        assertThatThrownBy(() -> useCase.execute(command(id, SELLER, UUID.randomUUID(), withPrice("96000.00"))))
                .isInstanceOf(ContractConflictException.class)
                .hasMessageContaining("frozen");
    }

    /** Two writers load the same version; the second save must fail instead of silently overwriting the first. */
    @Test
    void concurrentWriterIsRejectedByTheVersion() {
        String id = offer();
        Contract sellerCopy = contracts.findById(new ContractId(id)).orElseThrow();
        Contract buyerCopy = contracts.findById(new ContractId(id)).orElseThrow();

        sellerCopy.reviseCoffeeTerms(SELLER, canonical(withPrice("96000.00")));
        contracts.save(sellerCopy);
        buyerCopy.reviseCoffeeTerms(BUYER, canonical(withPrice("97000.00")));

        assertThatThrownBy(() -> contracts.save(buyerCopy)).isInstanceOf(OptimisticLockingFailureException.class);
        assertThat(jdbc.queryForObject("SELECT agreed_price FROM contracts WHERE contract_id = ?", String.class, id))
                .isEqualTo("96000.00");
    }

    private String offer() {
        String id = UUID.randomUUID().toString();
        contracts.save(Contract.offerCoffee(new ContractId(id), goods().listingId().toString(), BUYER, SELLER,
                "HTX Mua", "HTX Bán", "b@x.test", "s@x.test", canonical(input())));
        return id;
    }

    private CanonicalTerms canonical(ContractTermsInput input) {
        return CanonicalTerms.of(ContractTermsSnapshot.assemble(input, goods(), template, rules), canonicalizer);
    }

    private String hashOf(String id) {
        return jdbc.queryForObject("SELECT signed_content_hash FROM contracts WHERE contract_id = ?", String.class, id);
    }

    private static UpdateContractTermsCommand command(String id, String actor, UUID key, ContractTermsInput terms) {
        return new UpdateContractTermsCommand(id, actor, key, terms);
    }

    private static ContractTermsInput withPrice(String price) {
        ContractTermsInput t = input();
        return new ContractTermsInput(VndAmount.parse(price), t.currency(), t.milestoneSchedule(), t.qualityTerms(),
                t.deliveryTerms(), t.inspectionTerms(), t.priceBand(), t.priceAdjustmentRule(), t.toleranceRate(),
                t.shortfallPenaltyThreshold(), t.buyerPenaltyRate(), t.sellerPenaltyRate(),
                t.forceMajeureReportWindowDays(), t.buyerConfirmWindowDays(), t.buyerDepositRate(),
                t.disputeFloorReleaseRate(), t.sellerDepositRate(), t.level2InspectorOrg(), t.legalProfile());
    }

    /** A buyer signature row as #97 writes it (consent + verified challenge + signature), straight in SQL. */
    private void signAsBuyer(String contractId) {
        String hash = contracts.findById(new ContractId(contractId)).orElseThrow().getCoffeeTerms().signedContentHash();
        String consentId = UUID.randomUUID().toString();
        String otpId = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO consent_evidence (consent_id, action_type, subject_type, subject_id, actor_user_id,"
                + " actor_role, statement_code, statement_version, statement_hash, presented_content_hash,"
                + " consented_at) VALUES (?, 'SIGN_CONTRACT', 'CONTRACT', ?, ?, 'BUYER', 'SIGN_CONTRACT_AS_BUYER',"
                + " '1.0', ?, ?, NOW(6))", consentId, contractId, BUYER, "c".repeat(64), hash);
        jdbc.update("INSERT INTO signature_otp (otp_id, consent_id, contract_id, signer_role, user_id, auth_time,"
                + " signed_content_hash, otp_hash, sent_at, expires_at, verified_at) VALUES (?, ?, ?, 'BUYER', ?,"
                + " NOW(6), ?, ?, NOW(6), NOW(6) + INTERVAL 5 MINUTE, NOW(6))",
                otpId, consentId, contractId, BUYER, hash, "e".repeat(64));
        jdbc.update("INSERT INTO signature (signature_id, contract_id, signer_role, signer_user_id, otp_id,"
                + " consent_id, auth_time, signed_at, signed_content_hash) VALUES (?, ?, 'BUYER', ?, ?, ?, NOW(6),"
                + " NOW(6), ?)", UUID.randomUUID().toString(), contractId, BUYER, otpId, consentId, hash);
    }
}
