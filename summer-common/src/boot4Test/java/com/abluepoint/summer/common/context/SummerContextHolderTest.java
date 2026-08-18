package com.abluepoint.summer.common.context;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class SummerContextHolderTest {

    @AfterEach
    void clearContext() {
        SummerContextHolder.clear();
    }

    @Test
    void restoresNestedContext() {
        SummerContext outer = new SummerContext("outer", Locale.CHINA, Map.of());
        SummerContext inner = new SummerContext("inner", Locale.US, Map.of());

        try (SummerContextHolder.Scope ignored = SummerContextHolder.open(outer)) {
            try (SummerContextHolder.Scope nested = SummerContextHolder.open(inner)) {
                assertThat(SummerContextHolder.requireCurrent()).isEqualTo(inner);
            }
            assertThat(SummerContextHolder.requireCurrent()).isEqualTo(outer);
        }

        assertThat(SummerContextHolder.current()).isEmpty();
    }

    @Test
    void propagatesCapturedContextWithoutLeakingIt() {
        AtomicReference<SummerContext> observed = new AtomicReference<>();
        SummerContext context = new SummerContext("request-1", Locale.CHINA, Map.of("tenant", "demo"));
        Runnable wrapped;

        try (SummerContextHolder.Scope ignored = SummerContextHolder.open(context)) {
            wrapped = SummerContextHolder.wrap(() -> observed.set(SummerContextHolder.requireCurrent()));
        }

        wrapped.run();

        assertThat(observed).hasValue(context);
        assertThat(SummerContextHolder.current()).isEmpty();
    }

    @Test
    void rejectsNullContextAtPublicBindingBoundary() {
        assertThatNullPointerException()
                .isThrownBy(() -> SummerContextHolder.open(null))
                .withMessage("context");
    }
}
