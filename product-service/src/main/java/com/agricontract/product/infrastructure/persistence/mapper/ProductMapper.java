package com.agricontract.product.infrastructure.persistence.mapper;

import com.agricontract.product.domain.model.Product;
import com.agricontract.product.domain.model.vo.ProductId;
import com.agricontract.product.infrastructure.persistence.entity.ProductJpaEntity;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class ProductMapper {

    private final ObjectMapper objectMapper;

    public ProductJpaEntity toJpaEntity(Product domain) {
        return ProductJpaEntity.builder()
                .productId(domain.getProductId().value())
                .name(domain.getName())
                .unit(domain.getUnit())
                .categoryId(domain.getCategoryId())
                .images(serializeImages(domain.getImages()))
                .build();
    }

    public Product toDomain(ProductJpaEntity entity) {
        return Product.reconstruct(
                new ProductId(entity.getProductId()),
                entity.getName(),
                entity.getUnit(),
                entity.getCategoryId(),
                deserializeImages(entity.getImages())
        );
    }

    private String serializeImages(List<String> images) {
        try {
            return objectMapper.writeValueAsString(images);
        } catch (JsonProcessingException ex) {
            throw new RuntimeException("Failed to serialize product images", ex);
        }
    }

    private List<String> deserializeImages(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (JsonProcessingException ex) {
            throw new RuntimeException("Failed to deserialize product images", ex);
        }
    }
}
