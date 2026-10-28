package com.abluepoint.summer.sample.observability.service;

import io.opentelemetry.api.trace.Span;

/** Reads the actual OpenTelemetry context; it never invents correlation IDs. */
@org.springframework.modulith.NamedInterface("correlation")
public final class TraceCorrelation {
    private TraceCorrelation() { }

    public static String currentTraceId() {
        var context = Span.current().getSpanContext();
        return context.isValid() ? context.getTraceId() : null;
    }
}
