package com.abluepoint.summer.sample.observability.configuration;

import com.abluepoint.summer.sample.observability.controller.TraceResponseFilter;
import com.abluepoint.summer.sample.observability.service.TracedRequestLogSink;

import com.abluepoint.summer.mvc.logging.spi.RequestLogSink;
import com.abluepoint.summer.sample.cache.service.VersionedTwoLevelCache;
import com.abluepoint.summer.sample.realtime.RealtimeWebSocketHub;
import io.micrometer.core.instrument.FunctionCounter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.binder.MeterBinder;
import io.micrometer.core.instrument.config.MeterFilter;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

import java.util.Set;

@Configuration(proxyBeanMethods = false)
class ObservabilityConfiguration {
    @Bean
    RequestLogSink tracedRequestLogSink() { return new TracedRequestLogSink(); }

    @Bean
    FilterRegistrationBean<TraceResponseFilter> traceResponseFilter() {
        var registration = new FilterRegistrationBean<>(new TraceResponseFilter());
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 19);
        return registration;
    }

    @Bean
    MeterFilter monitoringTagBoundary() {
        // Fixed categories only: no principal, request/trace ID, URL, cache key or tenant.
        Set<String> allowed = Set.of("application", "status", "method", "outcome", "exception", "error",
                "result", "tier", "area", "id", "action", "cause", "name", "state", "type",
                "pool", "gc", "algorithm", "generation", "level", "kind", "class",
                "spring.security.authentication.type", "spring.security.authorization.decision",
                "spring.security.object", "spring.security.filterchain.name", "spring.security.filterchain.position");
        return new MeterFilter() {
            @Override public Meter.Id map(Meter.Id id) {
                return id.replaceTags(id.getTags().stream().filter(tag -> allowed.contains(tag.getKey())).toList());
            }
        };
    }

    @Bean
    MeterBinder sampleModuleMetrics(ObjectProvider<VersionedTwoLevelCache> caches,
                                    ObjectProvider<RealtimeWebSocketHub> hubs) {
        return registry -> {
            caches.ifAvailable(cache -> {
                FunctionCounter.builder("summer.cache.requests", cache, c -> c.statistics().requests()).register(registry);
                FunctionCounter.builder("summer.cache.hits", cache, c -> c.statistics().localHits()).tag("tier", "local").register(registry);
                FunctionCounter.builder("summer.cache.hits", cache, c -> c.statistics().remoteHits()).tag("tier", "redis").register(registry);
                FunctionCounter.builder("summer.cache.loads", cache, c -> c.statistics().loads()).register(registry);
                FunctionCounter.builder("summer.cache.redis.failures", cache, VersionedTwoLevelCache::redisFailureCount).register(registry);
                Gauge.builder("summer.cache.entries", cache, c -> c.statistics().localEntries()).tag("tier", "local").register(registry);
            });
            hubs.ifAvailable(hub -> Gauge.builder("summer.realtime.connections", hub,
                    RealtimeWebSocketHub::activeConnectionCount).register(registry));
        };
    }
}
