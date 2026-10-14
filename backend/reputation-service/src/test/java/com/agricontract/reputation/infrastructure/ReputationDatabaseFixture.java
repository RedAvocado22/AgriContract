package com.agricontract.reputation.infrastructure;

import java.nio.file.Files;
import java.nio.file.Path;
import org.flywaydb.core.Flyway;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.containers.MySQLContainer;

/** Shared disposable database, with production migrations and the actual runtime grants. */
final class ReputationDatabaseFixture {
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0.42")
            .withDatabaseName("reputation_db").withUsername("reputation_migration")
            .withPassword("test-only-migration-password").withEnv("MYSQL_ROOT_HOST", "%")
            .withUrlParam("connectionTimeZone", "UTC").withUrlParam("forceConnectionTimeZoneToSession", "true");
    static final JdbcTemplate ADMIN;
    static {
        MYSQL.start(); // No disabled-without-Docker condition: missing integration infrastructure is a failure.
        Flyway.configure().dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
                .locations("classpath:db/migration").load().migrate();
        ADMIN = new JdbcTemplate(new DriverManagerDataSource(MYSQL.getJdbcUrl(), "root", MYSQL.getPassword()));
        ADMIN.execute("CREATE USER 'reputation_runtime'@'%' IDENTIFIED BY 'test-only-runtime-password'");
        try {
            Path grants = Path.of(System.getProperty("contracts.directory"), "../backend/reputation-service/ops/grants.sql");
            String sql = Files.readString(grants).replaceAll("(?m)--[^\\r\\n]*", "");
            for (String statement : sql.split(";")) if (!statement.isBlank()) ADMIN.execute(statement);
        } catch (Exception e) { throw new ExceptionInInitializerError(e); }
    }
    private ReputationDatabaseFixture() {}
    static void properties(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", MYSQL::getJdbcUrl);
        r.add("spring.datasource.username", () -> "reputation_runtime");
        r.add("spring.datasource.password", () -> "test-only-runtime-password");
        r.add("spring.flyway.url", MYSQL::getJdbcUrl);
        r.add("spring.flyway.user", MYSQL::getUsername);
        r.add("spring.flyway.password", MYSQL::getPassword);
    }
}
