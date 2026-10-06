package com.agricontract.product.application.dto;

import com.agricontract.product.domain.model.vo.CategoryStatus;
import lombok.Builder;

@Builder
public record CategoryResponse(
        String categoryId,
        String name,
        CategoryStatus status,
        String rejectionReason,
        String proposedBy
) {}
