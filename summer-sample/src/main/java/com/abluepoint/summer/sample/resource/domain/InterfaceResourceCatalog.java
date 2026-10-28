package com.abluepoint.summer.sample.resource.domain;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class InterfaceResourceCatalog {

    private final List<InterfaceResourceDescriptor> items;
    private final List<InterfaceResourceDiscoveryIssue> issues;

    public InterfaceResourceCatalog(List<InterfaceResourceDescriptor> descriptors) { this(descriptors, List.of()); }

    public InterfaceResourceCatalog(List<InterfaceResourceDescriptor> descriptors, List<InterfaceResourceDiscoveryIssue> issues) {
        this.issues = issues.stream().distinct().sorted(Comparator.comparing(InterfaceResourceDiscoveryIssue::contextId)
                .thenComparing(InterfaceResourceDiscoveryIssue::source).thenComparing(InterfaceResourceDiscoveryIssue::code)).toList();
        Map<String, InterfaceResourceDescriptor> byKey = new LinkedHashMap<>();
        for (InterfaceResourceDescriptor descriptor : descriptors) {
            InterfaceResourceDescriptor previous = byKey.putIfAbsent(conflictKey(descriptor), descriptor);
            if (previous != null && !previous.equals(descriptor)) {
                throw new IllegalStateException("Conflicting interface resource: " + descriptor.key());
            }
        }
        List<InterfaceResourceDescriptor> sorted = new ArrayList<>(byKey.values());
        for (int i = 0; i < sorted.size(); i++) for (int j = i + 1; j < sorted.size(); j++) {
            if (ambiguous(sorted.get(i), sorted.get(j)))
                throw new IllegalStateException("Conflicting interface resource: " + sorted.get(j).key());
        }
        sorted.sort(Comparator.comparing(InterfaceResourceDescriptor::contextId).thenComparing(InterfaceResourceDescriptor::normalizedPath)
                .thenComparing(InterfaceResourceDescriptor::httpMethod)
                .thenComparing(item -> item.conditions().toString()).thenComparing(InterfaceResourceDescriptor::handler));
        this.items = List.copyOf(sorted);
    }

    private static String conflictKey(InterfaceResourceDescriptor item) {
        String canonicalPattern = item.normalizedPath().replaceAll("\\{[A-Za-z_][A-Za-z0-9_]*(?=[:}])", "{_");
        return item.contextId() + " " + item.httpMethod() + " " + canonicalPattern + " " + item.conditions();
    }

    private static boolean ambiguous(InterfaceResourceDescriptor left, InterfaceResourceDescriptor right) {
        if (left.handler().equals(right.handler()) || !routeKey(left).equals(routeKey(right))) return false;
        var a = new java.util.TreeMap<>(left.conditions());
        var b = new java.util.TreeMap<>(right.conditions());
        List<String> ap = a.remove("params"), bp = b.remove("params"), ah = a.remove("headers"), bh = b.remove("headers");
        return a.equals(b) && equalSpecificity(ap, bp) && equalSpecificity(ah, bh)
                && compatible(ap, bp) && compatible(ah, bh);
    }

    private static String routeKey(InterfaceResourceDescriptor item) {
        return item.contextId() + " " + item.httpMethod() + " "
                + item.normalizedPath().replaceAll("\\{[A-Za-z_][A-Za-z0-9_]*(?=[:}])", "{_");
    }

    private static boolean equalSpecificity(List<String> a, List<String> b) {
        a = a == null ? List.of() : a; b = b == null ? List.of() : b;
        return a.size() == b.size() && a.stream().filter(v -> v.contains("=") && !v.contains("!=")).count()
                == b.stream().filter(v -> v.contains("=") && !v.contains("!=")).count();
    }

    private static boolean compatible(List<String> a, List<String> b) {
        var expressions = new ArrayList<String>();
        if (a != null) expressions.addAll(a); if (b != null) expressions.addAll(b);
        Map<String, String> values = new java.util.HashMap<>();
        var present = new java.util.HashSet<String>(); var absent = new java.util.HashSet<String>();
        var excluded = new java.util.HashMap<String, java.util.Set<String>>();
        for (String expression : expressions) {
            int equals = expression.indexOf('=');
            if (equals < 0) {
                if (expression.startsWith("!")) absent.add(expression.substring(1)); else present.add(expression);
            } else {
                boolean negated = equals > 0 && expression.charAt(equals - 1) == '!';
                String name = expression.substring(0, negated ? equals - 1 : equals), value = expression.substring(equals + 1);
                if (negated) excluded.computeIfAbsent(name, ignored -> new java.util.HashSet<>()).add(value);
                else { present.add(name); String previous = values.putIfAbsent(name, value); if (previous != null && !previous.equals(value)) return false; }
            }
        }
        return java.util.Collections.disjoint(present, absent)
                && values.entrySet().stream().noneMatch(e -> excluded.getOrDefault(e.getKey(), java.util.Set.of()).contains(e.getValue()));
    }

    public List<InterfaceResourceDescriptor> items() {
        return items;
    }

    public List<InterfaceResourceDiscoveryIssue> issues() { return issues; }

    public long revision() {
        return Integer.toUnsignedLong(items.hashCode());
    }
}
