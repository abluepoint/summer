package com.abluepoint.summer.sample.customer;

import java.util.List;

record CustomerPage(
        List<?> items,
        int page,
        int size,
        long totalElements,
        long totalPages,
        String projection
) {
}
