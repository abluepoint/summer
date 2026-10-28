package com.abluepoint.summer.mvc.error;

import com.abluepoint.summer.common.error.api.CommonErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultErrorStatusResolverTest {

    private final DefaultErrorStatusResolver resolver = new DefaultErrorStatusResolver();

    @Test
    void mapsErrorCategoriesWithoutParsingErrorCodeStrings() {
        assertThat(resolver.resolve(CommonErrorCode.INVALID_ARGUMENT)).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resolver.resolve(CommonErrorCode.UNAUTHORIZED)).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(resolver.resolve(CommonErrorCode.FORBIDDEN)).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(resolver.resolve(CommonErrorCode.NOT_FOUND)).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(resolver.resolve(CommonErrorCode.CONFLICT)).isEqualTo(HttpStatus.CONFLICT);
        assertThat(resolver.resolve(CommonErrorCode.INTERNAL_ERROR)).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
