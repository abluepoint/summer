package com.abluepoint.summer.sample.resource.infrastructure.discovery;

import com.abluepoint.summer.sample.resource.domain.ResourceType;
import com.abluepoint.summer.sample.resource.domain.SensitiveLevel;
import org.springframework.web.util.pattern.PathPattern;


import com.abluepoint.summer.sample.resource.domain.InterfaceResourceDescriptor;

import org.springframework.http.HttpMethod;
import org.springframework.web.servlet.function.*;
import org.springframework.web.util.pattern.PathPatternParser;
import java.util.*;

/** Visits the registered router tree; does not execute handlers or resource lookup functions. */
final class FunctionalResourceDiscovery implements RouterFunctions.Visitor {
    private final List<InterfaceResourceDescriptor> items = new ArrayList<>();
    private final Deque<List<Branch>> nesting = new ArrayDeque<>();
    private final Runnable onUnsupported;
    private FunctionalResourceDiscovery(Runnable onUnsupported) { this.onUnsupported = onUnsupported; }

    static List<InterfaceResourceDescriptor> discover(RouterFunction<?> router) {
        return discover(router, () -> {});
    }

    static List<InterfaceResourceDescriptor> discover(RouterFunction<?> router, Runnable onUnsupported) {
        var visitor = new FunctionalResourceDiscovery(onUnsupported);
        router.accept(visitor);
        return List.copyOf(visitor.items);
    }

    @Override public void startNested(RequestPredicate predicate) {
        try { nesting.push(parse(predicate)); }
        catch (IllegalStateException unsupported) { onUnsupported.run(); nesting.push(List.of()); }
    }
    @Override public void endNested(RequestPredicate predicate) { nesting.pop(); }
    @Override public void route(RequestPredicate predicate, HandlerFunction<?> handler) {
        try { collectRoute(predicate, handler); } catch (IllegalStateException unsupported) { onUnsupported.run(); }
    }

    private void collectRoute(RequestPredicate predicate, HandlerFunction<?> handler) {
        List<Branch> branches = parse(predicate);
        for (List<Branch> prefix : nesting) branches = combine(prefix, branches, true);
        String identity = handler.getClass().isSynthetic() ? handler.getClass().getNestHost().getName() + "#functional"
                : handler.getClass().getName();
        for (Branch branch : branches) {
            String path = branch.path == null ? "/**" : branch.path;
            for (String method : branch.methods) items.add(new InterfaceResourceDescriptor(method, path, identity,
                    "functional", ResourceType.BUSINESS, SensitiveLevel.SENSITIVE, null, false,
                    "application", "FUNCTIONAL", branch.conditions, "MANAGED_ADAPTER_REQUIRED"));
        }
    }
    @Override public void resources(java.util.function.Function<ServerRequest, Optional<org.springframework.core.io.Resource>> lookup) {
        var field = org.springframework.util.ReflectionUtils.findField(lookup.getClass(), "pattern", PathPattern.class);
        if (field == null) { onUnsupported.run(); return; }
        org.springframework.util.ReflectionUtils.makeAccessible(field);
        String path = ((PathPattern) org.springframework.util.ReflectionUtils.getField(field, lookup)).getPatternString();
        for (String method : List.of("GET", "HEAD")) items.add(new InterfaceResourceDescriptor(method, path,
                lookup.getClass().getName(), "web", ResourceType.STATIC, SensitiveLevel.NORMAL, null, false,
                "application", "FUNCTIONAL", Map.of(), "STATIC_MAPPING"));
    }
    @Override public void attributes(Map<String, Object> attributes) { /* Arbitrary attributes may contain sensitive values. */ }
    @Override public void unknown(RouterFunction<?> router) { onUnsupported.run(); }

    private static List<Branch> parse(RequestPredicate predicate) {
        var visitor = new PredicateVisitor(); predicate.accept(visitor);
        if (visitor.stack.size() != 1) throw unsupported("predicate tree");
        return visitor.stack.pop();
    }

    private static List<Branch> combine(List<Branch> left, List<Branch> right, boolean nested) {
        List<Branch> result = new ArrayList<>();
        for (Branch a : left) for (Branch b : right) {
            Set<String> methods = new TreeSet<>(a.methods); methods.retainAll(b.methods);
            if (methods.isEmpty()) continue;
            String path = a.path == null ? b.path : b.path == null ? a.path : a.path;
            if (a.path != null && b.path != null && !a.path.equals(b.path)) {
                if (!nested) throw unsupported("intersecting path predicates require an explicit contributor");
                path = PathPatternParser.defaultInstance.parse(a.path).combine(PathPatternParser.defaultInstance.parse(b.path)).getPatternString();
            }
            Map<String, List<String>> conditions = new TreeMap<>(a.conditions);
            b.conditions.forEach((key, values) -> conditions.merge(key, values, (x, y) -> {
                var merged = new ArrayList<>(x); merged.addAll(y); return List.copyOf(merged);
            }));
            result.add(new Branch(methods, path, conditions));
        }
        return result;
    }

    private record Branch(Set<String> methods, String path, Map<String, List<String>> conditions) {}
    private static final Set<String> METHODS = Set.of("GET", "HEAD", "POST", "PUT", "PATCH", "DELETE", "OPTIONS", "TRACE");
    private static IllegalStateException unsupported(String reason) {
        return new IllegalStateException("SUMMER_RESOURCE_DISCOVERY_UNSUPPORTED: " + reason);
    }

    private static final class PredicateVisitor implements RequestPredicates.Visitor {
        private final Deque<List<Branch>> stack = new ArrayDeque<>();
        private void leaf(Set<String> methods, String path, Map<String,List<String>> conditions) {
            stack.push(List.of(new Branch(methods, path, conditions)));
        }
        private void condition(String name, String value) { leaf(METHODS, null, Map.of(name, List.of(value))); }
        @Override public void method(Set<HttpMethod> methods) {
            Set<String> names = new TreeSet<>(); methods.forEach(method -> names.add(method.name())); leaf(names, null, Map.of());
        }
        @Override public void path(String pattern) { leaf(METHODS, pattern, Map.of()); }
        @Override public void pathExtension(String extension) { condition("extension", extension); }
        @Override public void header(String name, String value) { condition("headers", SpringInterfaceResourceDiscovery.safeExpression(name + "=" + value)); }
        @Override public void param(String name, String value) { condition("params", SpringInterfaceResourceDiscovery.safeExpression(name + "=" + value)); }
        @Override public void version(String version) { condition("version", version); }
        @Override public void startAnd() {}
        @Override public void and() {}
        @Override public void endAnd() { var right = stack.pop(); var left = stack.pop(); stack.push(combine(left, right, false)); }
        @Override public void startOr() {}
        @Override public void or() {}
        @Override public void endOr() { var right = stack.pop(); var left = new ArrayList<>(stack.pop()); left.addAll(right); stack.push(left); }
        @Override public void startNegate() { throw unsupported("negated functional predicate requires an explicit contributor"); }
        @Override public void endNegate() {}
        @Override public void unknown(RequestPredicate predicate) { throw unsupported("opaque functional predicate requires an explicit contributor"); }
    }
}
