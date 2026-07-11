package com.agricontract.product.application.dto;

import jakarta.validation.constraints.NotBlank;

public record RejectCategoryRequest(
        @NotBlank String reason
) {}
