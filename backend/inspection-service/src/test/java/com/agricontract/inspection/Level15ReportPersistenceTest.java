package com.agricontract.inspection;

import com.agricontract.calendar.VietnamBusinessDays;
import com.agricontract.inspection.application.exception.*;
import com.agricontract.inspection.application.port.InspectorEligibilityPort;
import com.agricontract.inspection.application.usecase.Level15ReportPersistence;
import com.agricontract.inspection.domain.model.*;
import com.agricontract.inspection.domain.repository.Level15ReportRepository;
import com.agricontract.inspection.infrastructure.persistence.JdbcLevel15ReportRepository;
import java.sql.Timestamp;
import java.sql.SQLException;
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
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest(properties={"SERVICE_INTERNAL_SECRET=test", "GATEWAY_INTERNAL_SECRET=test",
        "VIETNAM_HOLIDAYS=2026-01-01", "inspection.assignment.scheduler-enabled=false",
        "inspection.dlq-monitor.enabled=false", "spring.rabbitmq.listener.simple.auto-startup=false",
        "management.health.rabbit.enabled=false"})
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
    @Autowired VietnamBusinessDays businessDays;
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
        Instant assignedAt = NOW.minusSeconds(3600);
        int dueBusinessDays = 1;
        jdbc.update("""
                INSERT INTO level1_5_inspection_assignment
                (commission_id,contract_id,milestone_id,inspector_id,assigned_at,ended_at,assigned_by_service,
                 report_due_business_days,report_due_at)
                VALUES (?,?,?,?,?,?,'contract-service',?,?)
                """,id,c,m,inspector,Timestamp.from(assignedAt),ended==null?null:Timestamp.from(ended),
                dueBusinessDays,Timestamp.from(businessDays.deadline(assignedAt,dueBusinessDays)));
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
        assertDatabaseRejects(() -> new TransactionTemplate(transactions).executeWithoutResult(
                s -> reports.insert(second,evidence(),NOW)), 1062, "uk_report_l15_commission");
    }

    @Test void freshDatabaseHasAllFourMigrationsAndCompleteReportSchema() {
        assertThat(jdbc.queryForList("SELECT version FROM flyway_schema_history WHERE success=1 ORDER BY installed_rank",
                String.class)).containsExactly("1", "2", "3", "4");
        var expected = """
                report_id|char(36)|NO
                l15_commission_id|char(36)|YES
                contract_id|char(36)|YES
                milestone_id|char(36)|YES
                inspector_id|char(36)|YES
                tier|varchar(10)|NO
                status|varchar(20)|NO
                content|json|NO
                normalized_result|json|YES
                report_file_hash|varchar(64)|NO
                result_hash|varchar(64)|YES
                report_hash|varchar(64)|YES
                external_org|varchar(255)|YES
                confirmed_by_operator_id|char(36)|YES
                received_via|varchar(50)|YES
                ingestion_source|varchar(20)|YES
                spf_dkim_result|varchar(20)|YES
                commission_id|char(36)|YES
                external_verification_status|varchar(20)|YES
                external_verification_checked_at|timestamp(6)|YES
                confirmed_at|timestamp(6)|YES
                created_at|timestamp(6)|NO
                """.lines().toList();
        assertThat(jdbc.queryForList("""
                SELECT CONCAT(column_name,'|',column_type,'|',is_nullable)
                FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='inspection_report'
                """, String.class)).containsExactlyInAnyOrderElementsOf(expected);
        assertThat(jdbc.queryForObject("""
                SELECT column_default FROM information_schema.columns
                WHERE table_schema=DATABASE() AND table_name='inspection_report' AND column_name='status'
                """, String.class)).isEqualTo("CONFIRMED");
        assertThat(jdbc.queryForObject("""
                SELECT LOWER(column_default) FROM information_schema.columns
                WHERE table_schema=DATABASE() AND table_name='inspection_report' AND column_name='created_at'
                """, String.class)).isEqualTo("current_timestamp(6)");
        assertThat(jdbc.queryForList("""
                SELECT CONCAT(column_name,':',referenced_column_name) FROM information_schema.key_column_usage
                WHERE constraint_schema=DATABASE() AND constraint_name='fk_report_l15_assignment'
                ORDER BY ordinal_position
                """, String.class)).containsExactly("l15_commission_id:commission_id", "contract_id:contract_id",
                        "milestone_id:milestone_id", "inspector_id:inspector_id");
        assertThat(jdbc.queryForList("""
                SELECT column_name FROM information_schema.statistics WHERE table_schema=DATABASE()
                AND table_name='level1_5_inspection_assignment' AND index_name='uk_l15_report_binding'
                AND non_unique=0 ORDER BY seq_in_index
                """, String.class)).containsExactly("commission_id", "contract_id", "milestone_id", "inspector_id");
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM information_schema.referential_constraints
                WHERE constraint_schema=DATABASE() AND table_name='inspection_report'
                """, Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("""
                SELECT collation_name FROM information_schema.columns
                WHERE table_schema=DATABASE() AND table_name='inspection_report' AND column_name='commission_id'
                """, String.class)).isEqualTo("ascii_bin");
    }

    @ParameterizedTest
    @ValueSource(strings={"contract_id", "milestone_id", "inspector_id", "l15_commission_id",
            "confirmed_at", "normalized_result", "result_hash", "report_hash"})
    void missingLevel15RequiredFieldFailsItsCheck(String column) {
        var row = level15Row();
        row.put(column, null);
        assertDatabaseRejects(() -> insertRow(row), 3819, "chk_report_l15_required");
    }

    @ParameterizedTest
    @ValueSource(strings={"contract_id", "milestone_id", "inspector_id", "l15_commission_id"})
    void mismatchedAssignmentBindingFailsForeignKey(String column) {
        var row = level15Row();
        row.put(column, id());
        assertDatabaseRejects(() -> insertRow(row), 1452, "fk_report_l15_assignment");
    }

    @ParameterizedTest
    @ValueSource(strings={"PENDING_REVIEW", "REJECTED", "UNKNOWN"})
    void level15CannotUseAnotherStatus(String status) {
        var row = level15Row();
        row.put("status", status);
        assertDatabaseRejects(() -> insertRow(row), 3819, "chk_report_l15_required");
    }

    @ParameterizedTest
    @ValueSource(strings={"external_org", "commission_id"})
    void level15CannotUseLevel2Attribution(String column) {
        var row = level15Row();
        row.put(column, id());
        assertDatabaseRejects(() -> insertRow(row), 3819, "chk_report_l15_required");
    }

    @Test void invalidTierIsRejected() {
        var row = level2Row();
        row.put("tier", "UNKNOWN");
        assertDatabaseRejects(() -> insertRow(row), 3819, "chk_report_tier");
    }

    @ParameterizedTest
    @ValueSource(strings={"report_file_hash", "result_hash", "report_hash"})
    void malformedNonNullHashIsRejectedEvenWithOtherHashesNull(String column) {
        var row = level2Row();
        for (String malformed : List.of("bad-hash", "A".repeat(64), "a".repeat(63))) {
            row.put(column, malformed);
            assertDatabaseRejects(() -> insertRow(row), 3819, "chk_l15_report_hashes");
        }
    }

    @Test void reportFileHashRemainsMandatoryForLevel2() {
        var row = level2Row();
        row.put("report_file_hash", null);
        assertDatabaseRejects(() -> insertRow(row), 1048, "report_file_hash");
    }

    @ParameterizedTest
    @ValueSource(strings={"PENDING_REVIEW", "REJECTED"})
    void nullableLevel2RowsAreStoredWithoutMappingAsLevel15(String status) {
        var first = level2Row();
        first.put("status", status);
        insertRow(first);
        var second = level2Row();
        second.put("status", status);
        insertRow(second); // Multiple NULL l15_commission_id values must remain legal.
        assertThat(jdbc.queryForObject("""
                SELECT contract_id IS NULL AND milestone_id IS NULL AND inspector_id IS NULL
                AND l15_commission_id IS NULL AND normalized_result IS NULL AND result_hash IS NULL
                AND report_hash IS NULL AND confirmed_at IS NULL AND created_at IS NOT NULL
                FROM inspection_report WHERE report_id=?
                """, Boolean.class, first.get("report_id"))).isTrue();
        assertThat(reports.find((String) first.get("report_id"))).isEmpty();
        assertThatThrownBy(() -> service.load((String) first.get("report_id")))
                .isInstanceOf(InspectionAssignmentAccessDeniedException.class);
        assertThat(service.pending()).extracting(InspectionAssignment::commissionId).containsExactly(commission);
    }

    @Test void level2ConfirmationMayFollowIntakeCreationAndUsesDefaultStatus() {
        var row = level2Row();
        row.remove("status");
        row.put("contract_id", contract);
        row.put("milestone_id", milestone);
        row.put("normalized_result", evidence().normalizedResult());
        row.put("result_hash", evidence().resultHash());
        row.put("report_hash", evidence().reportHash());
        row.put("confirmed_by_operator_id", id());
        row.put("confirmed_at", Timestamp.from(NOW));
        row.put("created_at", Timestamp.from(NOW.minusSeconds(3600)));
        insertRow(row);
        assertThat(jdbc.queryForObject("SELECT status FROM inspection_report WHERE report_id=?",
                String.class, row.get("report_id"))).isEqualTo("CONFIRMED");
        assertThat(reports.find((String) row.get("report_id"))).isEmpty();
    }

    @Test void level15StillRejectsConfirmationAfterCreation() {
        var row = level15Row();
        row.put("created_at", Timestamp.from(NOW.minusSeconds(1)));
        assertDatabaseRejects(() -> insertRow(row), 3819, "chk_l15_report_times");
    }

    @Test void concurrentSqlInsertsCannotBypassAssignmentUniqueness() throws Exception {
        var rows = List.of(level15Row(), level15Row());
        var start = new CountDownLatch(1);
        var successes = new AtomicInteger();
        var duplicates = new AtomicInteger();
        try (var pool = Executors.newFixedThreadPool(2)) {
            var futures = new ArrayList<Future<?>>();
            for (var row : rows) {
                futures.add(pool.submit(() -> {
                    try {
                        start.await();
                        insertRow(row); // No application lock or pre-insertion existence check.
                        successes.incrementAndGet();
                    } catch (DataAccessException failure) {
                        var cause = failure.getMostSpecificCause();
                        assertThat(cause).isInstanceOf(SQLException.class);
                        assertThat(((SQLException) cause).getErrorCode()).isEqualTo(1062);
                        assertThat(cause.getMessage()).contains("uk_report_l15_commission");
                        duplicates.incrementAndGet();
                    } catch (InterruptedException failure) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException(failure);
                    }
                }));
            }
            start.countDown();
            for (var future : futures) {
                future.get(20, TimeUnit.SECONDS);
            }
        }
        assertThat(successes).hasValue(1);
        assertThat(duplicates).hasValue(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM inspection_report WHERE l15_commission_id=?",
                Integer.class, commission)).isEqualTo(1);
    }

    private Map<String,Object> level15Row() {
        var row = new LinkedHashMap<String,Object>();
        row.put("report_id", id());
        row.put("l15_commission_id", commission);
        row.put("contract_id", contract);
        row.put("milestone_id", milestone);
        row.put("inspector_id", actor);
        row.put("tier", "LEVEL_1_5");
        row.put("content", "{}");
        row.put("normalized_result", evidence().normalizedResult());
        row.put("report_file_hash", evidence().reportFileHash());
        row.put("result_hash", evidence().resultHash());
        row.put("report_hash", evidence().reportHash());
        row.put("confirmed_at", Timestamp.from(NOW));
        row.put("created_at", Timestamp.from(NOW));
        return row;
    }

    private Map<String,Object> level2Row() {
        var row = new LinkedHashMap<String,Object>();
        row.put("report_id", id());
        row.put("tier", "LEVEL_2");
        row.put("status", "PENDING_REVIEW");
        row.put("content", "{}");
        row.put("report_file_hash", evidence().reportFileHash());
        row.put("external_org", "Test laboratory");
        row.put("received_via", "EMAIL");
        row.put("ingestion_source", "AUTO_EMAIL");
        return row;
    }

    private void insertRow(Map<String,Object> row) {
        // Column names are fixed test inputs; all values are bound parameters.
        jdbc.update("INSERT INTO inspection_report (" + String.join(",", row.keySet()) + ") VALUES ("
                + String.join(",", Collections.nCopies(row.size(), "?")) + ")", row.values().toArray());
    }

    private void assertDatabaseRejects(Runnable operation, int errorCode, String constraint) {
        var failure = catchThrowable(operation::run);
        assertThat(failure).isInstanceOf(DataAccessException.class);
        var cause = ((DataAccessException) failure).getMostSpecificCause();
        assertThat(cause).isInstanceOf(SQLException.class);
        assertThat(((SQLException) cause).getErrorCode()).isEqualTo(errorCode);
        assertThat(cause.getMessage()).contains(constraint);
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

    @Test void readsRunReadOnlyAndJoinAnExistingWriteTransaction() {
        when(users.load(actor)).thenAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
            assertThat(TransactionSynchronizationManager.isCurrentTransactionReadOnly()).isTrue();
            return new InspectorEligibility(actor,"INSPECTOR","VERIFIED",null);
        });
        var identity = allocate();
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            doAnswer(invocation -> {
                assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
                assertThat(TransactionSynchronizationManager.isCurrentTransactionReadOnly()).isFalse();
                return new InspectorEligibility(actor,"INSPECTOR","VERIFIED",null);
            }).when(users).load(actor);
            service.persist(identity, evidence());
            assertThat(service.load(identity.reportId()).reportId()).isEqualTo(identity.reportId());
        });
        assertThat(reports.find(identity.reportId())).isPresent();
    }

    @Test void caughtParticipatingFailureMarksCallerRollbackOnly() {
        var identity = allocate();
        assertThatThrownBy(() -> new TransactionTemplate(transactions).executeWithoutResult(status -> {
            service.persist(identity, evidence());
            try {
                service.persist(identity, evidence());
                fail("duplicate report must fail");
            } catch (IllegalStateException expected) {
                assertThat(expected).hasMessageContaining("already has a confirmed report");
            }
        })).isInstanceOf(UnexpectedRollbackException.class);
        assertThat(reports.find(identity.reportId())).isEmpty();
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
