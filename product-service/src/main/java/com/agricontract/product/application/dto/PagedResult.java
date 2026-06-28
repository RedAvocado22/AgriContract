package com.agricontract.product.application.dto;

import java.util.List;

public record PagedResult<T>(List<T> content, long totalElements, int totalPages) {}
