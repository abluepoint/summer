package com.abluepoint.summer.sample.resource;

import com.abluepoint.summer.security.authorization.ResourceAuthorization.Domain;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import com.abluepoint.summer.sample.dictionary.InterfaceServiceNames;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

@Service
final class InterfaceServiceDirectory {
    private final JdbcTemplate jdbc;
    private final InterfaceResourceCatalogService catalog;
    private final InterfaceResourceHistory history;
    private final ManagedConsumerAuthorization guard;

    private final InterfaceServiceNames dictionary;

    InterfaceServiceDirectory(JdbcTemplate jdbc, InterfaceResourceCatalogService catalog,
                              InterfaceResourceHistory history, ManagedConsumerAuthorization guard, InterfaceServiceNames dictionary) {
        this.dictionary = dictionary;
        this.jdbc = jdbc;
        this.catalog = catalog;
        this.history = history;
        this.guard = guard;
    }

    record Binding(String serviceCode, String serviceName, long revision, Instant lastSyncedAt) {}
    record Row(String serviceCode, String serviceName, long revision, Instant lastSyncedAt, @JsonUnwrapped InterfaceResourceDescriptor resource) {}
    record Page(List<Row> items, long total, int page, int size, long revision, List<String> serviceNames,
                List<InterfaceServiceNames.Option> services, Set<String> policies, Set<InterfaceResourceDiscoveryIssue> issues, boolean complete) {}

    synchronized Binding refresh(Authentication actor, String code, String name) {
        if (!guard.require(Domain.platform(), actor, "platform.capabilities.write"))
            throw new org.springframework.security.access.AccessDeniedException("Interface refresh denied");
        var matches = dictionary.published().stream().filter(InterfaceServiceNames.Option::enabled)
                .filter(option -> code != null ? option.value().equals(code) : option.label().equals(name)).toList();
        if (matches.size() != 1) throw new IllegalArgumentException("请选择已发布且启用的服务名称");
        var option = matches.getFirst();
        long revision = catalog.refresh().revision();
        Instant lastSyncedAt = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        Timestamp timestamp = Timestamp.from(lastSyncedAt);
        jdbc.update("insert into summer_interface_service_catalog (service_code, service_name, revision, last_synced_at) values (?, ?, ?, ?) "
                + "on duplicate key update revision = ?, service_name = ?, last_synced_at = ?",
                option.value(), option.label(), revision, timestamp, revision, option.label(), timestamp);
        return new Binding(option.value(), option.label(), revision, lastSyncedAt);
    }

    Page page(Authentication actor, int page, int size, Long revision, String path, String policy, String name, String code) {
        catalog.requireRead(actor);
        InterfaceResourceCatalogPage.validate(page, size);
        var current = catalog.viewAt(revision);
        var options = new ArrayList<>(dictionary.published());
        var labels = new HashMap<String,String>();
        options.forEach(option -> labels.put(option.value(), option.label()));
        var bindings = jdbc.query("select service_code, service_name, revision, last_synced_at from summer_interface_service_catalog order by service_code",
                (rs, i) -> {
                    Timestamp synced = rs.getTimestamp("last_synced_at");
                    return new Binding(rs.getString(1), labels.getOrDefault(rs.getString(1), rs.getString(2)),
                            rs.getLong(3), synced == null ? null : synced.toInstant());
                });
        for (var binding : bindings) {
            if (!labels.containsKey(binding.serviceCode()))
                options.add(new InterfaceServiceNames.Option(binding.serviceCode(), binding.serviceName(), false));
        }
        var names = bindings.stream().map(Binding::serviceName).toList();
        if (bindings.isEmpty()) bindings = List.of(new Binding("", "", current.revision(), null));
        Map<Long, InterfaceResourceCatalogView> views = new HashMap<>();
        views.put(current.revision(), current);
        List<Row> rows = new ArrayList<>(size);
        Set<String> policies = new TreeSet<>();
        Set<InterfaceResourceDiscoveryIssue> issues = new LinkedHashSet<>();
        long offset = (long) page * size;
        long total = 0;
        for (var binding : bindings) {
            if (code != null && !code.isEmpty() && !code.equals(binding.serviceCode())) continue;
            if (name != null && !name.isEmpty() && !name.equals(binding.serviceName())) continue;
            var view = views.computeIfAbsent(binding.revision(), history::read);
            issues.addAll(view.issues());
            for (var item : view.items()) {
                policies.add(item.configurationPolicy());
                if (policy != null && !policy.isEmpty() && !policy.equals(item.configurationPolicy())) continue;
                if (path != null && !item.normalizedPath().contains(path.strip())) continue;
                if (total >= offset && rows.size() < size) rows.add(new Row(binding.serviceCode(), binding.serviceName(), binding.revision(), binding.lastSyncedAt(), item));
                total++;
            }
        }
        return new Page(rows, total, page, size, current.revision(), names, options, policies, issues, issues.isEmpty());
    }
}
