package com.agricontract.product.infrastructure.persistence.mapper;

import com.agricontract.product.domain.model.Product;
import com.agricontract.product.domain.model.vo.ProductId;
import com.agricontract.product.infrastructure.persistence.entity.ProductJpaEntity;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface ProductMapper {
    default ProductJpaEntity toJpaEntity(Product domain) {
        return ProductJpaEntity.builder()
                .productId(domain.getProductId().value())
                .name(domain.getName())
                .unit(domain.getUnit())
                .category(domain.getCategory())
                .build();
    }

    default Product toDomain(ProductJpaEntity entity) {
        return Product.reconstruct(
                new ProductId(entity.getProductId()),
                entity.getName(),
                entity.getUnit(),
                entity.getCategory()
        );
    }
}
