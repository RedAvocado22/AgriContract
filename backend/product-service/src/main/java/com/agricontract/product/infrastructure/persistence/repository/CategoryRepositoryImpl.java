package com.agricontract.product.infrastructure.persistence.repository;

import com.agricontract.product.common.exception.DuplicateCategoryException;
import com.agricontract.product.domain.event.DomainEvent;
import com.agricontract.product.domain.model.Category;
import com.agricontract.product.domain.model.vo.CategoryId;
import com.agricontract.product.domain.repository.CategoryRepository;
import com.agricontract.product.infrastructure.persistence.entity.CategoryJpaEntity;
import com.agricontract.product.infrastructure.persistence.entity.ProductDomainEventJpaEntity;
import com.agricontract.product.infrastructure.persistence.mapper.CategoryMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class CategoryRepositoryImpl implements CategoryRepository {

    private final CategoryJpaRepository categoryJpaRepository;
    private final ProductDomainEventJpaRepository eventRepository;
    private final CategoryMapper categoryMapper;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public Category save(Category category) {
        CategoryJpaEntity entity = categoryMapper.toJpaEntity(category);
        categoryJpaRepository.findByCategoryId(category.getCategoryId().value())
                .ifPresent(existing -> entity.setId(existing.getId()));

        try {
            categoryJpaRepository.save(entity);
        } catch (DataIntegrityViolationException e) {
            throw new DuplicateCategoryException(category.getName());
        }

        String categoryId = category.getCategoryId().value();
        List<DomainEvent> events = category.pullDomainEvents();
        events.stream().map(event -> toOutboxEntity(event, categoryId)).forEach(eventRepository::save);

        return category;
    }

    @Override
    public Optional<Category> findById(CategoryId categoryId) {
        return categoryJpaRepository.findByCategoryId(categoryId.value())
                .map(categoryMapper::toDomain);
    }

    @Override
    public Optional<Category> findByNormalizedName(String normalizedName) {
        return categoryJpaRepository.findByNormalizedName(normalizedName)
                .map(categoryMapper::toDomain);
    }

    private ProductDomainEventJpaEntity toOutboxEntity(DomainEvent event, String aggregateId) {
        try {
            String payload = objectMapper.writeValueAsString(event);
            return ProductDomainEventJpaEntity.builder()
                    .eventId(event.getEventId().toString())
                    .eventType(event.getEventType())
                    .aggregateId(aggregateId)
                    .payload(payload)
                    .status(ProductDomainEventJpaEntity.Status.PENDING)
                    .build();
        } catch (JsonProcessingException ex) {
            throw new RuntimeException("Failed to serialize domain event: " + event.getEventType(), ex);
        }
    }
}
