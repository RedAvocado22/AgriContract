package com.agricontract.inspection;

import com.agricontract.inspection.application.exception.*;
import com.agricontract.inspection.application.port.InspectorEligibilityPort;
import com.agricontract.inspection.application.usecase.Level15ReportPersistence;
import com.agricontract.inspection.domain.model.*;
import com.agricontract.inspection.domain.repository.Level15ReportRepository;
import com.agricontract.inspection.infrastructure.persistence.JdbcLevel15ReportRepository;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.*;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.*;
import org.springframework.transaction.*;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest(properties={"SERVICE_INTERNAL_SECRET=test", "GATEWAY_INTERNAL_SECRET=test"})
@Testcontainers
class Level15ReportPersistenceTest {
    @Container static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0.42");
    @DynamicPropertySource static void database(DynamicPropertyRegistry p) {
        p.add("spring.datasource.url", () -> MYSQL.getJdbcUrl()
                +(MYSQL.getJdbcUrl().contains("?")?"&":"?")+"connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true");
        p.add("spring.datasource.username",MYSQL::getUsername);
        p.add("spring.datasource.password",MYSQL::getPassword);
    }
    static final Instant NOW = Instant.parse("2026-10-25T03:00:00Z");
    @Autowired Level15ReportPersistence service;
    @Autowired Level15ReportRepository reports;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactions;
    @MockBean InspectorEligibilityPort users;
    @MockBean Clock clock;
    String actor,contract,milestone,commission;

    @BeforeEach void setup() {
        actor=id(); contract=id(); milestone=id(); commission=id();
        when(clock.instant()).thenReturn(NOW);
        when(users.load(actor)).thenReturn(new InspectorEligibility(actor,"INSPECTOR","VERIFIED",null));
        authenticate(actor,"INSPECTOR");
        assignment(commission,contract,milestone,actor,null);
    }
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }
    static String id() { return UUID.randomUUID().toString(); }
    static void authenticate(String actor,String role) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(actor,null,
                List.of(new SimpleGrantedAuthority("ROLE_"+role))));
    }
    void assignment(String id,String c,String m,String inspector,Instant ended) {
        jdbc.update("""
                INSERT INTO level1_5_inspection_assignment
                (commission_id,contract_id,milestone_id,inspector_id,assigned_at,ended_at,assigned_by_service)
                VALUES (?,?,?,?,?,?,'contract-service')
                """,id,c,m,inspector,Timestamp.from(NOW.minusSeconds(3600)),ended==null?null:Timestamp.from(ended));
    }
    ReportIdentity allocate() { return service.allocateIdentity(contract,milestone); }
    // Synthetic commitments are storage fixtures only, NOT evidence of hashing/signature integration.
    ReportEvidence evidence() {
        return new ReportEvidence("{}","""
                {"resultSchemaVersion":"1.0","measuredQuantityKg":10,"acceptedQuantityKg":10,
                 "measurementStatus":"COMPLETE","actualQualityMetrics":{"commodity":"COFFEE","type":"ROBUSTA",
                 "moisturePercent":12,"foreignMatterPercent":0,"blackBrokenBeansPercent":0},
                 "determinedAt":"2026-10-25T02:59:00Z"}
                ""","a".repeat(64),"b".repeat(64),"c".repeat(64),NOW);
    }
    void store(ReportIdentity identity) {
        new TransactionTemplate(transactions).executeWithoutResult(s -> service.persist(identity,evidence()));
    }

    @Test void authorizedIdentityIsServerGeneratedAndSurvivesRepositoryRecreation() {
        var identity=allocate(); assertThat(UUID.fromString(identity.reportId())).isNotNull();
        store(identity);
        var reloaded=new JdbcLevel15ReportRepository(jdbc).find(identity.reportId()).orElseThrow();
        assertThat(reloaded.reportId()).isEqualTo(identity.reportId());
        assertThat(reloaded.commissionId()).isEqualTo(commission);
        assertThat(reloaded.contractId()).isEqualTo(contract);
        assertThat(reloaded.milestoneId()).isEqualTo(milestone);
        assertThat(reloaded.inspectorId()).isEqualTo(actor);
        assertThat(service.load(identity.reportId())).isEqualTo(reloaded);
        assertThat(service.pending()).isEmpty();
        assertThat(jdbc.queryForObject("SELECT ended_at IS NULL FROM level1_5_inspection_assignment WHERE commission_id=?",Boolean.class,commission)).isTrue();
    }

    @ParameterizedTest @ValueSource(strings={"ADMIN","OPERATOR","ARBITRATOR","BUYER","SELLER"})
    void otherRolesCannotAllocatePersistOrQuery(String role) {
        var identity=allocate(); authenticate(actor,role);
        assertThatThrownBy(this::allocate).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> store(identity)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(service::pending).isInstanceOf(AccessDeniedException.class);
    }

    @Test void missingIdentityIsRejected() {
        SecurityContextHolder.clearContext();
        assertThatThrownBy(service::pending).isInstanceOf(AuthenticationCredentialsNotFoundException.class);
    }

    @Test void ineligibleAndUnavailableUserFailClosed() {
        when(users.load(actor)).thenReturn(new InspectorEligibility(actor,"INSPECTOR","REJECTED",null));
        assertThatThrownBy(service::pending).isInstanceOf(InspectorNotEligibleException.class);
        when(users.load(actor)).thenThrow(new EligibilityUnavailableException());
        assertThatThrownBy(this::allocate).isInstanceOf(EligibilityUnavailableException.class);
    }

    @Test void otherInspectorCannotAccessReportOrCommission() {
        var identity=allocate(); store(identity);
        var other=id(); when(users.load(other)).thenReturn(new InspectorEligibility(other,"INSPECTOR","VERIFIED",null));
        authenticate(other,"INSPECTOR");
        assertThatThrownBy(this::allocate).isInstanceOf(InspectionAssignmentAccessDeniedException.class);
        assertThatThrownBy(() -> service.load(identity.reportId())).isInstanceOf(InspectionAssignmentAccessDeniedException.class);
        assertThat(service.pending()).isEmpty();
    }

    @Test void closedAssignmentsAreRecheckedAfterIdentityAllocation() {
        var identity=allocate();
        jdbc.update("UPDATE level1_5_inspection_assignment SET ended_at=? WHERE commission_id=?",Timestamp.from(NOW),commission);
        assertThatThrownBy(() -> store(identity)).isInstanceOf(InspectionAssignmentAccessDeniedException.class);
        assertThat(service.pending()).isEmpty();
    }

    @Test void mismatchedRelationshipsFailBothApplicationAndDatabase() {
        var forged=ReportIdentity.allocate(new InspectionAssignment(commission,id(),milestone,actor,NOW.minusSeconds(10),null,"contract-service",null));
        assertThatThrownBy(() -> store(forged)).isInstanceOf(InspectionAssignmentAccessDeniedException.class);
        assertThatThrownBy(() -> new TransactionTemplate(transactions).executeWithoutResult(s -> reports.insert(forged,evidence(),NOW)))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> service.allocateIdentity(contract,id())).isInstanceOf(InspectionAssignmentAccessDeniedException.class);
    }

    @Test void storedIdentityCannotBeReplacedThroughPersistenceBoundary() {
        var identity=allocate(); store(identity);
        var before=service.load(identity.reportId());
        var changed=new ReportEvidence("{\"changed\":true}",evidence().normalizedResult(),"d".repeat(64),"e".repeat(64),"f".repeat(64),NOW);
        assertThatThrownBy(() -> new TransactionTemplate(transactions).executeWithoutResult(s -> reports.insert(identity,changed,NOW)))
                .isInstanceOf(DataAccessException.class);
        assertThat(service.load(identity.reportId())).isEqualTo(before);
        assertThat(Arrays.stream(Level15ReportRepository.class.getMethods()).map(java.lang.reflect.Method::getName))
                .doesNotContain("save","merge","update","delete");
        assertThatThrownBy(() -> jdbc.update("UPDATE level1_5_inspection_assignment SET inspector_id=? WHERE commission_id=?",id(),commission))
                .isInstanceOf(DataAccessException.class);
        jdbc.update("UPDATE level1_5_inspection_assignment SET ended_at=? WHERE commission_id=?",Timestamp.from(NOW),commission);
        assertThat(service.load(identity.reportId())).isEqualTo(before);
    }

    @Test void pendingQueryReturnsOnlyOwnedActiveWorkWithoutConfirmedReport() {
        assignment(id(),contract,milestone,actor,NOW.minusSeconds(20));
        assignment(id(),contract,milestone,actor,NOW.minusSeconds(10));
        assignment(id(),id(),id(),id(),null);
        assertThat(service.pending()).extracting(InspectionAssignment::commissionId).containsExactly(commission);
        store(allocate()); assertThat(service.pending()).isEmpty();
    }

    @Test void duplicateIdentityAndSecondReportForSameAssignmentAreRejected() {
        var first=allocate(); var second=allocate(); store(first);
        assertThatThrownBy(() -> store(first)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> store(second)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new TransactionTemplate(transactions).executeWithoutResult(s -> reports.insert(first,evidence(),NOW)))
                .isInstanceOf(DataAccessException.class);
    }

    @Test void databaseRejectsUnsupportedStateMissingBindingAndInvalidCommitments() {
        var identity=allocate(); store(identity);
        for (var invalid:Map.of("contract_id","NULL","tier","'LEVEL_2'",
                "status","'PENDING_REVIEW'","report_hash","'bad-hash'").entrySet()) {
            String contractValue=invalid.getKey().equals("contract_id")?invalid.getValue():"contract_id";
            String tierValue=invalid.getKey().equals("tier")?invalid.getValue():"tier";
            String stateValue=invalid.getKey().equals("status")?invalid.getValue():"status";
            String hashValue=invalid.getKey().equals("report_hash")?invalid.getValue():"report_hash";
            assertThatThrownBy(() -> jdbc.update("""
                    INSERT INTO inspection_report
                    (report_id,l15_commission_id,contract_id,milestone_id,inspector_id,tier,status,
                     content,normalized_result,report_file_hash,result_hash,report_hash,confirmed_at,created_at)
                    SELECT ?,l15_commission_id,%s,milestone_id,inspector_id,%s,%s,
                           content,normalized_result,report_file_hash,result_hash,%s,confirmed_at,created_at
                    FROM inspection_report WHERE report_id=?
                    """.formatted(contractValue,tierValue,stateValue,hashValue),id(),identity.reportId()))
                    .isInstanceOf(DataAccessException.class);
        }
    }

    @Test void callerRollbackRemovesReportAndRestoresPendingWork() {
        var identity=allocate();
        assertThatThrownBy(() -> new TransactionTemplate(transactions).executeWithoutResult(s -> {
            service.persist(identity,evidence()); throw new IllegalStateException("caller failed");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(reports.find(identity.reportId())).isEmpty();
        assertThat(service.pending()).hasSize(1);
        assertThatThrownBy(() -> service.persist(identity,evidence())).isInstanceOf(IllegalTransactionStateException.class);
    }

    @Test void concurrentReportsForOneAssignmentCannotBothPersist() throws Exception {
        var identities=List.of(allocate(),allocate()); var start=new CountDownLatch(1); var successes=new AtomicInteger();
        try (var pool=Executors.newFixedThreadPool(2)) {
            var futures=new ArrayList<Future<?>>();
            for (var identity:identities) futures.add(pool.submit(() -> {
                authenticate(actor,"INSPECTOR");
                try { start.await(); store(identity); successes.incrementAndGet(); }
                catch (IllegalStateException expected) { /* Second confirmed report is a conflict. */ }
                catch (InterruptedException failure) { Thread.currentThread().interrupt(); throw new RuntimeException(failure); }
                finally { SecurityContextHolder.clearContext(); }
            }));
            start.countDown(); for (var future:futures) future.get(20,TimeUnit.SECONDS);
        }
        assertThat(successes).hasValue(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM inspection_report WHERE l15_commission_id=?",Integer.class,commission)).isEqualTo(1);
    }

    @Test void invalidConfirmationTimeAndIncompleteEvidenceAreRejected() {
        var identity=allocate(); var e=evidence();
        for (Instant at:List.of(NOW.plusSeconds(1),NOW.minusSeconds(7200))) {
            var invalid=new ReportEvidence(e.content(),e.normalizedResult(),e.reportFileHash(),e.resultHash(),e.reportHash(),at);
            assertThatThrownBy(() -> new TransactionTemplate(transactions).executeWithoutResult(s -> service.persist(identity,invalid)))
                    .isInstanceOf(IllegalArgumentException.class);
        }
        assertThatThrownBy(() -> new ReportEvidence("{}","{}","invalid",e.resultHash(),e.reportHash(),NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
