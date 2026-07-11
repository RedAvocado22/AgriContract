package com.agricontract.product.infrastructure.persistence.mapper;

import com.agricontract.product.domain.model.Category;
import com.agricontract.product.domain.model.vo.CategoryId;
import com.agricontract.product.infrastructure.persistence.entity.CategoryJpaEntity;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface CategoryMapper {

    default CategoryJpaEntity toJpaEntity(Category domain) {
        return CategoryJpaEntity.builder()
                .categoryId(domain.getCategoryId().value())
                .name(domain.getName())
                .normalizedName(domain.getNormalizedName())
                .status(domain.getStatus())
                .rejectionReason(domain.getRejectionReason())
                .proposedBy(domain.getProposedBy())
                .proposedByEmail(domain.getProposedByEmail())
                .build();
    }

    default Category toDomain(CategoryJpaEntity entity) {
        return Category.reconstruct(
                new CategoryId(entity.getCategoryId()),
                entity.getName(),
                entity.getNormalizedName(),
                entity.getStatus(),
                entity.getRejectionReason(),
                entity.getProposedBy(),
                entity.getProposedByEmail()
        );
    }
}
