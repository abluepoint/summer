package com.abluepoint.summer.common.context;

import java.util.Objects;
import java.util.Optional;

public final class SummerContextHolder {

    private static final ThreadLocal<SummerContext> CURRENT = new ThreadLocal<>();

    private SummerContextHolder() {
    }

    public static Optional<SummerContext> current() {
        return Optional.ofNullable(CURRENT.get());
    }

    public static SummerContext requireCurrent() {
        return current().orElseThrow(() -> new IllegalStateException("No SummerContext is bound to the current thread"));
    }

    public static Scope open(SummerContext context) {
        SummerContext previous = CURRENT.get();
        CURRENT.set(Objects.requireNonNull(context, "context"));
        return new Scope(previous);
    }

    public static Runnable wrap(Runnable task) {
        SummerContext captured = CURRENT.get();
        return () -> {
            try (Scope ignored = bindNullable(captured)) {
                task.run();
            }
        };
    }

    public static void clear() {
        CURRENT.remove();
    }

    private static Scope bindNullable(SummerContext context) {
        SummerContext previous = CURRENT.get();
        if (context == null) {
            CURRENT.remove();
        } else {
            CURRENT.set(context);
        }
        return new Scope(previous);
    }

    public static final class Scope implements AutoCloseable {
        private final SummerContext previous;
        private boolean closed;

        private Scope(SummerContext previous) {
            this.previous = previous;
        }

        @Override
        public void close() {
            if (closed) {
                return;
            }
            if (previous == null) {
                CURRENT.remove();
            } else {
                CURRENT.set(previous);
            }
            closed = true;
        }
    }
}
