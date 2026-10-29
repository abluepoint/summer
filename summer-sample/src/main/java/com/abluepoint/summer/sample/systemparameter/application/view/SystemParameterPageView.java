package com.abluepoint.summer.sample.systemparameter.application.view;

import java.util.List;

public record SystemParameterPageView(
        List<SystemParameterView> items,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
}
