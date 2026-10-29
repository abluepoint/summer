package com.abluepoint.summer.sample.systemparameter.application.view;

import java.util.List;

public record SystemParameterQueryPageView(
        List<?> items,
        int page,
        int size,
        long totalElements,
        int totalPages,
        String projection
) {
}
