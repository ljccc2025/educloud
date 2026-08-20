package com.educloud.common.api;

import java.util.List;
import java.util.Objects;

public record PageResponse<T>(
        List<T> items,
        int page,
        int pageSize,
        long total,
        long totalPages) {

    public PageResponse {
        items = List.copyOf(Objects.requireNonNull(items, "items"));
        validateInputs(page, pageSize, total);
        long expectedTotalPages = calculateTotalPages(total, pageSize);
        if (totalPages != expectedTotalPages) {
            throw new IllegalArgumentException("totalPages does not match total and pageSize");
        }
    }

    public static <T> PageResponse<T> of(List<T> items, int page, int pageSize, long total) {
        Objects.requireNonNull(items, "items");
        validateInputs(page, pageSize, total);
        return new PageResponse<>(items, page, pageSize, total, calculateTotalPages(total, pageSize));
    }

    private static void validateInputs(int page, int pageSize, long total) {
        if (page < 1) {
            throw new IllegalArgumentException("page must be at least 1");
        }
        if (pageSize < 1) {
            throw new IllegalArgumentException("pageSize must be positive");
        }
        if (total < 0) {
            throw new IllegalArgumentException("total must not be negative");
        }
    }

    private static long calculateTotalPages(long total, int pageSize) {
        return total == 0 ? 0 : 1 + ((total - 1) / pageSize);
    }
}
