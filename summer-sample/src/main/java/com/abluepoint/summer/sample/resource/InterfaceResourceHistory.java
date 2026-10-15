package com.abluepoint.summer.sample.resource;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

@Service
final class InterfaceResourceHistory {
    private final InterfaceResourceRevisionRepository repository;
    private final ObjectMapper mapper;
    private volatile Map<String, Set<String>> originalHandlers = Map.of();

    InterfaceResourceHistory(InterfaceResourceRevisionRepository repository, ObjectMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    InterfaceResourceCatalog protect(InterfaceResourceCatalog catalog) {
        Map<String, Set<String>> owners = new HashMap<>();
        repository.findAll().forEach(row -> view(row).items().stream().filter(InterfaceResourceHistory::managedAutomatic)
                .forEach(item -> owners.computeIfAbsent(bindingKey(item), ignored -> new HashSet<>()).add(item.handler())));
        originalHandlers = immutableOwners(owners);
        return new InterfaceResourceCatalog(catalog.items().stream().map(item -> {
            Set<String> previous = owners.get(bindingKey(item));
            if (managedAutomatic(item) && previous != null && !previous.contains(item.handler()))
                return new InterfaceResourceDescriptor(item.httpMethod(), item.normalizedPath(), item.handler(), item.module(),
                        item.resourceType(), item.sensitiveLevel(), item.permissionCode(), false, item.contextId(), item.source(),
                        item.conditions(), "REPLACED_REQUIRES_EXPLICIT_PERMISSION");
            return item;
        }).toList(), catalog.issues());
    }

    boolean isReplaced(String permissionCode, String handler) {
        Set<String> owners = originalHandlers.get("application:" + permissionCode);
        return owners != null && !owners.contains(handler);
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

    private void remember(InterfaceResourceCatalog catalog) {
        Map<String, Set<String>> owners = new HashMap<>();
        originalHandlers.forEach((key, value) -> owners.put(key, new HashSet<>(value)));
        catalog.items().stream().filter(InterfaceResourceHistory::managedAutomatic)
                .forEach(item -> owners.computeIfAbsent(bindingKey(item), ignored -> new HashSet<>()).add(item.handler()));
        originalHandlers = immutableOwners(owners);
    }

    InterfaceResourceCatalogView record(InterfaceResourceCatalog catalog) {
        String json = mapper.writeValueAsString(new InterfaceResourceSnapshot(catalog.items(), catalog.issues()));
        remember(catalog);
        String fingerprint = digest(json);
        var existing = repository.findByFingerprint(fingerprint);
        if (existing.isPresent()) return view(existing.get());
        try {
            return view(repository.saveAndFlush(new InterfaceResourceRevisionEntity(fingerprint, json, catalog.items().size())));
        } catch (DataIntegrityViolationException race) {
            // Concurrent nodes observing identical metadata converge on the immutable fingerprint.
            return repository.findByFingerprint(fingerprint).map(this::view).orElseThrow(() -> race);
        }
    }

    long count() { return repository.count(); }

    long latestRevision() {
        return repository.findAllByOrderByRevisionDesc(PageRequest.of(0, 1)).stream()
                .mapToLong(InterfaceResourceRevisionEntity::getRevision).findFirst().orElse(0);
    }

    List<InterfaceResourceRevisionSummary> list(int page, int size) {
        if (page < 0 || size < 1 || size > 100) throw new IllegalArgumentException("page must be non-negative and size must be between 1 and 100");
        return repository.findAllByOrderByRevisionDesc(PageRequest.of(page, size)).stream()
                .map(InterfaceResourceRevisionEntity::summary).toList();
    }

    InterfaceResourceCatalogView read(long revision) {
        if (revision < 1) throw new IllegalArgumentException("revision must be positive");
        return repository.findById(revision).map(this::view).orElseThrow(RevisionNotFoundException::new);
    }

    InterfaceResourceDifference diff(long from, long to) {
        var before = byKey(read(from).items());
        var after = byKey(read(to).items());
        List<InterfaceResourceDescriptor> added = new ArrayList<>(), removed = new ArrayList<>();
        List<InterfaceResourceChange> changed = new ArrayList<>();
        before.forEach((key, value) -> {
            var replacement = after.get(key);
            if (replacement == null) removed.add(value);
            else if (!value.equals(replacement)) changed.add(new InterfaceResourceChange(value, replacement,
                    value.handler().equals(replacement.handler()) ? "CHANGED" : "REPLACED"));
        });
        after.forEach((key, value) -> { if (!before.containsKey(key)) added.add(value); });
        return new InterfaceResourceDifference(from, to, List.copyOf(added), List.copyOf(removed), List.copyOf(changed));
    }

    private InterfaceResourceCatalogView view(InterfaceResourceRevisionEntity row) {
        if (row.getSnapshotJson().stripLeading().startsWith("[")) {
            var items = mapper.readValue(row.getSnapshotJson(), InterfaceResourceDescriptor[].class);
            return new InterfaceResourceCatalogView(List.of(items), row.getRevision());
        }
        var snapshot = mapper.readValue(row.getSnapshotJson(), InterfaceResourceSnapshot.class);
        return new InterfaceResourceCatalogView(snapshot.items(), row.getRevision(), snapshot.issues());
    }

    private static Map<String, InterfaceResourceDescriptor> byKey(List<InterfaceResourceDescriptor> items) {
        Map<String, InterfaceResourceDescriptor> result = new LinkedHashMap<>();
        items.forEach(item -> result.put(item.key(), item));
        return result;
    }

    static String digest(String text) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }

    static final class RevisionNotFoundException extends RuntimeException {}
}
