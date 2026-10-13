package com.agricontract.user.application.dto;

import java.util.List;

// Framework-free page of results; the web layer maps it to its own response shape.
public record PageResult<T>(
        List<T> content,
        int page,
        int size,
        long totalElements
) {
}
