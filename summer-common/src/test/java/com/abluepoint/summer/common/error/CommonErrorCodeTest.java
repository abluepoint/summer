package com.abluepoint.summer.common.error;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CommonErrorCodeTest {

    @Test
    void exposesSemanticFrameworkCodesAndTransportIndependentCategories() {
        assertThat(CommonErrorCode.values()).extracting(CommonErrorCode::code).containsExactly(
                "SUMMER_INVALID_ARGUMENT",
                "SUMMER_UNAUTHORIZED",
                "SUMMER_FORBIDDEN",
                "SUMMER_NOT_FOUND",
                "SUMMER_CONFLICT",
                "SUMMER_INTERNAL_ERROR"
        );
        assertThat(CommonErrorCode.values()).extracting(CommonErrorCode::category).containsExactly(
                ErrorCategory.INVALID_ARGUMENT,
                ErrorCategory.UNAUTHORIZED,
                ErrorCategory.FORBIDDEN,
                ErrorCategory.NOT_FOUND,
                ErrorCategory.CONFLICT,
                ErrorCategory.INTERNAL_ERROR
        );
    }
}
