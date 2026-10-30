package com.abluepoint.summer.sample.resource.domain;


import java.util.*;
import java.util.regex.Pattern;

/** Preserves observed ownership; only explicitly catalogued type and handler moves are equivalent. */
public final class InterfaceResourceBindingPolicy {
    private final Map<String, String> typeMoves;
    private final Map<String, String> handlerMoves;
    private final Pattern movedType;
    private volatile Map<String, Set<String>> originalHandlers = Map.of();

    public InterfaceResourceBindingPolicy(Map<String, String> typeMoves) {
        this(typeMoves, Map.of());
    }

    public InterfaceResourceBindingPolicy(Map<String, String> typeMoves, Map<String, String> handlerMoves) {
        this.typeMoves = Map.copyOf(typeMoves);
        this.handlerMoves = Map.copyOf(handlerMoves);
        String alternatives = typeMoves.keySet().stream().sorted(Comparator.comparingInt(String::length).reversed())
                .map(Pattern::quote).collect(java.util.stream.Collectors.joining("|"));
        this.movedType = alternatives.isEmpty() ? Pattern.compile("(?!)")
                : Pattern.compile("(?:(?<![A-Za-z0-9_$.])|(?<=\\[L))(" + alternatives + ")(?=$|[^A-Za-z0-9_.])");
    }

    private String canonical(String handler) {
        String observed = handlerMoves.getOrDefault(handler, handler);
        return movedType.matcher(observed).replaceAll(match -> java.util.regex.Matcher.quoteReplacement(typeMoves.get(match.group(1))));
    }

    public InterfaceResourceCatalog protect(InterfaceResourceCatalog catalog, List<InterfaceResourceCatalogView> revisions) {
        Map<String, Set<String>> owners = new HashMap<>();
        revisions.forEach(row -> row.items().stream().filter(InterfaceResourceBindingPolicy::managedAutomatic)
                .forEach(item -> owners.computeIfAbsent(bindingKey(item), ignored -> new HashSet<>()).add(canonical(item.handler()))));
        originalHandlers = immutableOwners(owners);
        return new InterfaceResourceCatalog(catalog.items().stream().map(item -> {
            Set<String> previous = owners.get(bindingKey(item));
            if (managedAutomatic(item) && previous != null && !previous.contains(canonical(item.handler())))
                return new InterfaceResourceDescriptor(item.httpMethod(), item.normalizedPath(), item.handler(), item.module(),
                        item.resourceType(), item.sensitiveLevel(), item.permissionCode(), false, item.contextId(), item.source(),
                        item.conditions(), "REPLACED_REQUIRES_EXPLICIT_PERMISSION");
            return item;
        }).toList(), catalog.issues());
    }

    public boolean isReplaced(String permissionCode, String handler) {
        Set<String> owners = originalHandlers.get("application:" + permissionCode);
        return owners != null && !owners.contains(canonical(handler));
    }

    private static boolean managedAutomatic(InterfaceResourceDescriptor item) {
        return item.resourceType() == ResourceType.BUSINESS && "MANAGED_ROLE".equals(item.configurationPolicy())
                && item.permissionCode().equals(item.httpMethod() + ":" + item.normalizedPath());
    }

    private static String bindingKey(InterfaceResourceDescriptor item) { return item.contextId() + ":" + item.permissionCode(); }
    private static Map<String, Set<String>> immutableOwners(Map<String, Set<String>> owners) {
        Map<String, Set<String>> copy = new HashMap<>();
        owners.forEach((key, value) -> copy.put(key, Set.copyOf(value)));
        return Map.copyOf(copy);
    }

    public void remember(InterfaceResourceCatalog catalog) {
        Map<String, Set<String>> owners = new HashMap<>();
        originalHandlers.forEach((key, value) -> owners.put(key, new HashSet<>(value)));
        catalog.items().stream().filter(InterfaceResourceBindingPolicy::managedAutomatic)
                .forEach(item -> owners.computeIfAbsent(bindingKey(item), ignored -> new HashSet<>()).add(canonical(item.handler())));
        originalHandlers = immutableOwners(owners);
    }

}
