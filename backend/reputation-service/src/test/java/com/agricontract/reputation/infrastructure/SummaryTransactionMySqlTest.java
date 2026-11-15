package com.agricontract.reputation.infrastructure;

import com.agricontract.reputation.application.dto.BatchSummaryQuery;
import com.agricontract.reputation.application.dto.PublicSummaryQuery;
import com.agricontract.reputation.application.port.TransactionRunner;
import com.agricontract.reputation.application.usecase.GetPublicReputationSummaries;
import com.agricontract.reputation.application.usecase.GetPublicReputationSummary;
import com.agricontract.reputation.infrastructure.persistence.JdbcReputationReadRepository;
import com.agricontract.reputation.infrastructure.persistence.SpringTransactionRunner;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.UnexpectedRollbackException;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@SpringBootTest(properties = {"gateway.internal-secret=transaction-test-secret",
        "spring.rabbitmq.username=unused", "spring.rabbitmq.password=unused",
        "spring.rabbitmq.listener.simple.auto-startup=false", "reputation.dlq-monitor.enabled=false"})
class SummaryTransactionMySqlTest {
    @Autowired TransactionRunner runner;
    @Autowired PlatformTransactionManager manager;
    @Autowired JdbcTemplate jdbc;
    @Autowired GetPublicReputationSummary summary;
    @Autowired GetPublicReputationSummaries summaries;
    @SpyBean(proxyTargetAware = true) JdbcReputationReadRepository repository;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        ReputationDatabaseFixture.properties(registry);
    }

    @Test
    void productionRunnerStartsReadOnlyRepeatableReadAndCleansUp() {
        assertThat(runner).isInstanceOf(SpringTransactionRunner.class);
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
        assertThat(runner.inRepeatableReadOnlyTransaction(() -> {
            assertReadTransaction();
            return "result";
        })).isEqualTo("result");
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
        assertThat(TransactionSynchronizationManager.hasResource(jdbc.getDataSource())).isFalse();
        assertThat(runner.<Object>inRepeatableReadOnlyTransaction(() -> null)).isNull();
    }

    @Test
    void requiredJoinsReadWriteReadCommittedWithoutIndependentCommit() {
        var outer = new TransactionTemplate(manager);
        outer.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        var commits = new AtomicInteger();
        UUID user = UUID.randomUUID();
        outer.executeWithoutResult(status -> {
            long connection = connectionId();
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { commits.incrementAndGet(); }
            });
            runner.inRepeatableReadOnlyTransaction(() -> {
                assertThat(connectionId()).isEqualTo(connection);
                assertThat(TransactionSynchronizationManager.isCurrentTransactionReadOnly()).isFalse();
                assertThat(TransactionSynchronizationManager.getCurrentTransactionIsolationLevel())
                        .isEqualTo(Connection.TRANSACTION_READ_COMMITTED);
                insertCompletion(user);
                return null;
            });
            assertThat(commits).hasValue(0);
            assertThat(ReputationDatabaseFixture.ADMIN.queryForObject(
                    "SELECT COUNT(*) FROM completed_contract_fact WHERE buyer_id=?", Integer.class, user.toString()))
                    .isZero();
            assertThat(summary.execute(new PublicSummaryQuery(user)).score()).isEqualByComparingTo("100");
            assertThat(summaries.execute(new BatchSummaryQuery(List.of(user))).items().getFirst().score())
                    .isEqualByComparingTo("100");
            status.setRollbackOnly();
        });
        assertThat(commits).hasValue(0);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM completed_contract_fact WHERE buyer_id=?",
                Integer.class, user.toString())).isZero();
    }

    @Test
    void runtimeFailurePropagatesAndRollsBackNewTransaction() {
        var failure = new IllegalStateException("read failed");
        var completion = new AtomicInteger(-1);
        assertThatThrownBy(() -> runner.inRepeatableReadOnlyTransaction(() -> {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCompletion(int status) { completion.set(status); }
            });
            throw failure;
        })).isSameAs(failure);
        assertThat(completion).hasValue(TransactionSynchronization.STATUS_ROLLED_BACK);
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
    }

    @Test
    void caughtParticipatingFailureStillRollsBackOuterTransaction() {
        var failure = new IllegalArgumentException("read failed");
        UUID user = UUID.randomUUID();
        assertThatThrownBy(() -> new TransactionTemplate(manager).executeWithoutResult(status -> {
            insertCompletion(user);
            assertThatThrownBy(() -> runner.inRepeatableReadOnlyTransaction(() -> { throw failure; }))
                    .isSameAs(failure);
        })).isInstanceOf(UnexpectedRollbackException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM completed_contract_fact WHERE buyer_id=?",
                Integer.class, user.toString())).isZero();
    }

    @Test
    void realRepositoryReadsKeepSnapshotAfterIndependentCommitThenFreshTransactionSeesIt() {
        UUID user = UUID.randomUUID();
        Instant now = Instant.now();
        runner.inRepeatableReadOnlyTransaction(() -> {
            assertReadTransaction();
            var before = repository.publicSummaries(List.of(user), now);
            assertThat(before.getFirst().score()).isNull();
            commitCompletionIndependently(user, connectionId());
            assertThat(repository.publicSummaries(List.of(user), now)).isEqualTo(before);
            return null;
        });
        assertThat(runner.inRepeatableReadOnlyTransaction(
                () -> repository.publicSummaries(List.of(user), now)).getFirst().score())
                .isEqualByComparingTo("100");
    }

    @Test
    void singleSummaryKeepsAggregateAndHistoryInOneSnapshot() {
        UUID user = UUID.randomUUID();
        var firstConnection = new long[1];
        doAnswer(call -> {
            assertReadTransaction();
            firstConnection[0] = connectionId();
            Object result = call.callRealMethod();
            // ADMIN uses a separate DriverManagerDataSource connection, with auto-commit.
            ReputationMySqlTest.lock(user, Instant.now().minusSeconds(60), Instant.now().plusSeconds(3600));
            assertThat(ReputationDatabaseFixture.ADMIN.queryForObject(
                    "SELECT COUNT(*) FROM lock_entry WHERE user_id=?", Integer.class, user.toString())).isEqualTo(1);
            return result;
        }).when(repository).publicSummaries(any(), any());
        doAnswer(call -> {
            assertReadTransaction();
            assertThat(connectionId()).isEqualTo(firstConnection[0]);
            return call.callRealMethod();
        }).when(repository).lockHistory(any(), any());
        var result = summary.execute(new PublicSummaryQuery(user));
        assertThat(result.score()).isNull();
        assertThat(result.locked()).isFalse();
        assertThat(result.lockHistory()).isEmpty();
        verify(repository).publicSummaries(any(), any());
        verify(repository).lockHistory(any(), any());
        reset(repository);
        var fresh = summary.execute(new PublicSummaryQuery(user));
        assertThat(fresh.locked()).isTrue();
        assertThat(fresh.lockHistory()).hasSize(1);
    }

    @Test
    void entireBatchUsesOneTransactionAndOneAggregateRead() {
        var ids = List.of(UUID.randomUUID(), UUID.randomUUID());
        var completions = new AtomicInteger();
        doAnswer(call -> {
            assertReadTransaction();
            assertThat(call.<List<UUID>>getArgument(0)).containsExactlyElementsOf(ids);
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { completions.incrementAndGet(); }
            });
            return call.callRealMethod();
        }).when(repository).publicSummaries(any(), any());
        assertThat(summaries.execute(new BatchSummaryQuery(ids)).items()).hasSize(2);
        verify(repository).publicSummaries(any(), any());
        verify(repository, never()).lockHistory(any(), any());
        assertThat(completions).hasValue(1);
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
    }

    private void assertReadTransaction() {
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
        assertThat(TransactionSynchronizationManager.isCurrentTransactionReadOnly()).isTrue();
        assertThat(TransactionSynchronizationManager.getCurrentTransactionIsolationLevel())
                .isEqualTo(Connection.TRANSACTION_REPEATABLE_READ);
        Connection connection = DataSourceUtils.getConnection(jdbc.getDataSource());
        try {
            assertThat(connection.getAutoCommit()).isFalse();
            assertThat(connection.isReadOnly()).isTrue();
            assertThat(connection.getTransactionIsolation()).isEqualTo(Connection.TRANSACTION_REPEATABLE_READ);
        } catch (SQLException e) {
            throw new AssertionError(e);
        } finally {
            DataSourceUtils.releaseConnection(connection, jdbc.getDataSource());
        }
    }

    private long connectionId() {
        return jdbc.queryForObject("SELECT CONNECTION_ID()", Long.class);
    }

    private void insertCompletion(UUID user) {
        jdbc.update("INSERT INTO completed_contract_fact VALUES (?,?,?,CURRENT_TIMESTAMP,?)",
                UUID.randomUUID().toString(), user.toString(), UUID.randomUUID().toString(), UUID.randomUUID().toString());
    }

    private void commitCompletionIndependently(UUID user, long readerConnection) {
        var mysql = ReputationDatabaseFixture.MYSQL;
        try (var connection = DriverManager.getConnection(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword())) {
            connection.setAutoCommit(false);
            try (var statement = connection.createStatement(); var result = statement.executeQuery("SELECT CONNECTION_ID()")) {
                assertThat(result.next()).isTrue();
                assertThat(result.getLong(1)).isNotEqualTo(readerConnection);
            }
            try (var insert = connection.prepareStatement(
                    "INSERT INTO completed_contract_fact VALUES (?,?,?,CURRENT_TIMESTAMP,?)")) {
                insert.setString(1, UUID.randomUUID().toString());
                insert.setString(2, user.toString());
                insert.setString(3, UUID.randomUUID().toString());
                insert.setString(4, UUID.randomUUID().toString());
                assertThat(insert.executeUpdate()).isEqualTo(1);
            }
            connection.commit(); // Synchronous commit finishes before the reader resumes; no sleeps or shared transaction.
        } catch (SQLException e) {
            throw new AssertionError(e);
        }
        assertThat(ReputationDatabaseFixture.ADMIN.queryForObject(
                "SELECT COUNT(*) FROM completed_contract_fact WHERE buyer_id=?", Integer.class, user.toString())).isEqualTo(1);
    }
}
