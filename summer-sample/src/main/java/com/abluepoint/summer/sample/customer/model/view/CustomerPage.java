package com.abluepoint.summer.sample.customer.model.view;

import java.util.List;

public record CustomerPage(
        List<?> items,
        int page,
        int size,
        long totalElements,
        long totalPages,
        String projection
) {
}
