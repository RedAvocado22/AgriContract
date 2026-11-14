package com.agricontract.product.infrastructure.persistence.repository;

import com.agricontract.product.domain.repository.CategoryRepository;
import com.agricontract.product.infrastructure.persistence.mapper.CategoryMapperImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

/** listApprovedCategories on real MySQL: APPROVED only, ordered by name, legacy commodity read back as null. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({CategoryRepositoryImpl.class, CategoryMapperImpl.class, JacksonAutoConfiguration.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Testcontainers(disabledWithoutDocker = true)
class CategoryPersistenceTest {

    @Container
    @ServiceConnection
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0");

    @Autowired private CategoryRepository categoryRepository;
    @Autowired private JdbcTemplate jdbcTemplate;

    private void insert(String id, String name, String status, String commodity) {
        jdbcTemplate.update("""
                        INSERT INTO categories (category_id, name, normalized_name, status, proposed_by,
                            proposed_by_email, commodity, created_at, updated_at)
                        VALUES (?, ?, ?, ?, 'seller-1', 'seller1@test.vn', ?, UTC_TIMESTAMP(), UTC_TIMESTAMP())""",
                id, name, id, status, commodity);
    }

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM categories");
    }

    @Test
    void approvedOnly_orderedByName_legacyCommodityNull() {
        insert("cat-3", "Tiêu", "APPROVED", null);
        insert("cat-1", "Cà phê", "APPROVED", "COFFEE");
        insert("cat-2", "Ca cao", "APPROVED", null);
        insert("cat-4", "Bơ", "PENDING", null);
        insert("cat-5", "Điều", "REJECTED", null);

        assertThat(categoryRepository.findAllApprovedOrderByName())
                .extracting(c -> c.getCategoryId().value() + ":" + c.getName() + ":" + c.getCommodity())
                .containsExactly("cat-2:Ca cao:null", "cat-1:Cà phê:COFFEE", "cat-3:Tiêu:null");
    }

    @Test
    void noApprovedCategory_isEmpty() {
        insert("cat-4", "Bơ", "PENDING", null);

        assertThat(categoryRepository.findAllApprovedOrderByName()).isEmpty();
    }
}
