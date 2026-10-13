package com.agricontract.user.common;

import org.springframework.data.domain.Page;

import java.util.List;

public record PaginatedResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last,
        boolean empty
) {
    // Same semantics as Spring's Page, for callers that only hold plain page/size/total values.
    public static <T> PaginatedResponse<T> of(List<T> content, int page, int size, long totalElements) {
        int totalPages = (int) Math.ceil((double) totalElements / size);
        return new PaginatedResponse<>(
                content,
                page,
                size,
                totalElements,
                totalPages,
                page == 0,
                page + 1 >= totalPages,
                content.isEmpty()
        );
    }

    public static <T> PaginatedResponse<T> from(Page<T> page) {
        return new PaginatedResponse<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isFirst(),
                page.isLast(),
                page.isEmpty()
        );
    }
}
