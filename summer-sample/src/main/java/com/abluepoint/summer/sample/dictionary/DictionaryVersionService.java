package com.abluepoint.summer.sample.dictionary;

import static com.abluepoint.summer.sample.configuration.ConfigurationTenantContext.resolve;
import com.abluepoint.summer.common.error.SummerException;
import com.abluepoint.summer.sample.authorization.DataScopeService;
import com.abluepoint.summer.sample.resilience.ResilientReadService;
import com.abluepoint.summer.sample.cache.VersionedTwoLevelCache;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.core.Authentication;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.UUID;

@Service
class DictionaryVersionService {
    private final DictionaryTypeRepository types;
    private final DictionaryItemRepository items;
    private final DictionaryVersionRepository versions;
    private final DictionaryOutboxRepository outbox;
    private final DictionaryAuditService audit;
    private final VersionedTwoLevelCache cache;
    private final DataScopeService dataScopes;
    private final ObjectMapper objectMapper;
    private final ResilientReadService resilientReads;
    private final com.abluepoint.summer.sample.resilience.ConfigurationFallbackGuard fallbackGuard;
    private final DictionaryNotificationService notifications;
    private final com.abluepoint.summer.sample.configuration.ConfigurationActionAccess actions;
    private final com.abluepoint.summer.sample.configuration.ConfigurationOverrideService overrides;

    DictionaryVersionService(DictionaryTypeRepository types, DictionaryItemRepository items,
                             DictionaryVersionRepository versions, DictionaryOutboxRepository outbox,
                             DictionaryAuditService audit, VersionedTwoLevelCache cache,
                             DataScopeService dataScopes, ObjectMapper objectMapper,
                             ResilientReadService resilientReads, DictionaryNotificationService notifications, com.abluepoint.summer.sample.configuration.ConfigurationOverrideService overrides, com.abluepoint.summer.sample.configuration.ConfigurationActionAccess actions, com.abluepoint.summer.sample.resilience.ConfigurationFallbackGuard fallbackGuard) {
        this.types = types;
        this.items = items;
        this.versions = versions;
        this.outbox = outbox;
        this.audit = audit;
        this.cache = cache;
        this.dataScopes = dataScopes;
        this.objectMapper = objectMapper;
        this.resilientReads = resilientReads;
        this.fallbackGuard = fallbackGuard;
        this.notifications = notifications;
        this.overrides = overrides;
        this.actions = actions;
    }

    @Transactional
    DictionaryTypeVersionView publish(String code, DictionaryVersionRequest request, String requestId,
                                      Authentication authentication) {
        DictionaryTypeEntity type = scopedType(code, authentication);
        DictionaryTypeVersionView replay = replay(code, request, type, "DICTIONARY_PUBLISHED");
        if (replay != null) return replay;
        long current = currentVersion(type);
        requireExpected(request, current);
        long next = versions.findTopByTenantIdAndDictionaryCodeOrderByVersionDesc(resolve(authentication), code)
                .map(value -> value.version() + 1).orElse(1L);
        String snapshot = snapshot(type, code);
        DictionaryVersionEntity version = new DictionaryVersionEntity(UUID.randomUUID().toString(), resolve(authentication),
                code, next, "PUBLISHED", snapshot, principal(authentication), requestId);
        type.publish(next);
        versions.saveAndFlush(version);
        types.saveAndFlush(type);
        evidence(code, next, "DICTIONARY_PUBLISHED", requestId, request == null ? null : request.idempotencyKey(), authentication);
        return view(type, version);
    }

    @Transactional
    DictionaryTypeVersionView disable(String code, DictionaryVersionRequest request, String requestId,
                                      Authentication authentication) {
        DictionaryTypeEntity type = scopedType(code, authentication);
        DictionaryTypeVersionView replay = replay(code, request, type, "DICTIONARY_DISABLED");
        if (replay != null) return replay;
        long current = currentVersion(type);
        requireExpected(request, current);
        if (type.publishedVersion() == null || !type.published()) {
            throw invalidState("Dictionary is not published");
        }
        type.disable();
        types.saveAndFlush(type);
        evidence(code, current, "DICTIONARY_DISABLED", requestId, request == null ? null : request.idempotencyKey(), authentication);
        return view(type, versions.findByTenantIdAndDictionaryCodeAndVersion(resolve(authentication), code, current).orElse(null));
    }

    @Transactional
    DictionaryTypeVersionView rollback(String code, DictionaryVersionRequest request, String requestId,
                                       Authentication authentication) {
        DictionaryTypeEntity type = scopedType(code, authentication);
        DictionaryTypeVersionView replay = replay(code, request, type, "DICTIONARY_ROLLED_BACK");
        if (replay != null) return replay;
        long current = currentVersion(type);
        requireExpected(request, current);
        if (request == null || request.targetVersion() == null || request.targetVersion() <= 0
                || request.targetVersion() == current) {
            throw invalidState("Rollback target is not a previous version");
        }
        DictionaryVersionEntity target = versions.findByTenantIdAndDictionaryCodeAndVersion(
                        resolve(authentication), code, request.targetVersion())
                .orElseThrow(() -> invalidState("Rollback target does not exist"));
        type.publish(target.version());
        types.saveAndFlush(type);
        evidence(code, target.version(), "DICTIONARY_ROLLED_BACK", requestId, request.idempotencyKey(), authentication);
        return view(type, target);
    }

    @Transactional(readOnly = true)
    List<DictionaryVersionView> list(String code, Authentication authentication) {
        scopedType(code, authentication);
        return versions.findAllByTenantIdAndDictionaryCodeOrderByVersionDesc(resolve(authentication), code).stream()
                .map(value -> new DictionaryVersionView(value.version(), value.status(), value.createdAt(), value.createdBy()))
                .toList();
    }

    @Transactional(readOnly = true)
    PublicDictionaryView publicRead(String code) { return publicRead(code, null); }

    @Transactional(readOnly = true)
    PublicDictionaryView publicRead(String code, Authentication authentication) { return publicRead(resolve(authentication), code, authentication); }

    @Transactional(readOnly = true)
    PublicDictionaryView publicRead(String tenant, String code, Authentication authentication) {
        actions.requirePublic(tenant, "dictionaries", authentication);
        DataScopeService.ScopeFilter scope = dataScopes.filter(tenant, "dictionaries", authentication);
        DictionaryTypeEntity type = types.findScoped(code, scope)
                .filter(t -> t.enabled() && t.publicReadable() && t.published())
                .orElseThrow(() -> new SummerException(com.abluepoint.summer.common.error.CommonErrorCode.NOT_FOUND));
        // Recheck both type and item scope before any cache/fallback read. Snapshot values remain immutable.
        List<DictionaryItemEntity> visible = items.findScoped(code, scope);
        java.util.Set<String> allowedValues = visible.stream().map(DictionaryItemEntity::value).collect(java.util.stream.Collectors.toSet());
        var override = overrides.resolve(tenant, "dictionaries", code, authentication);
        if (override.isPresent()) {
            List<DictionaryItemView> options = objectMapper.readValue(override.get().valueJson(), objectMapper.getTypeFactory().constructCollectionType(List.class, DictionaryItemView.class));
            return new PublicDictionaryView(code, options.stream().filter(DictionaryItemView::enabled).filter(i -> allowedValues.contains(i.value())).toList(), override.get().version());
        }
        if (type.publishedVersion() == null) {
            return new PublicDictionaryView(code, visible.stream().filter(DictionaryItemEntity::enabled)
                    .map(i -> new DictionaryItemView(i.value(), i.label(), i.sortOrder(), i.enabled())).toList(), 0);
        }
        DictionaryVersionEntity version = versions.findByTenantIdAndDictionaryCodeAndVersion(tenant, code, type.publishedVersion())
                .orElseThrow(() -> new SummerException(com.abluepoint.summer.common.error.CommonErrorCode.NOT_FOUND));
        DictionarySnapshot snapshot = objectMapper.readValue(version.snapshotJson(), DictionarySnapshot.class);
        if (!snapshot.publicReadable() || !snapshot.enabled()) throw new SummerException(com.abluepoint.summer.common.error.CommonErrorCode.NOT_FOUND);
        String cacheKey = tenant + ":" + code + ":" + (authentication == null ? "" : authentication.getName()) + ":" + version.version() + ":" + scope.fingerprint() + ":" + new java.util.TreeSet<>(allowedValues);
        return resilientReads.read(ResilientReadService.DICTIONARIES, cacheKey, () -> cache.get(tenant, "dictionary", cacheKey, PublicDictionaryView.class,
                () -> new PublicDictionaryView(code, snapshot.items().stream().filter(DictionaryItemView::enabled)
                        .filter(i -> allowedValues.contains(i.value())).toList(), version.version())),
                () -> fallbackGuard.dictionary(tenant, code, version.version(), scope.fingerprint(), allowedValues, authentication));
    }

    @Transactional
    com.abluepoint.summer.sample.configuration.ConfigurationOverrideView override(String code, String organization,
            com.abluepoint.summer.sample.configuration.ConfigurationOverrideRequest request, Authentication auth, String requestId) {
        scopedType(code, auth);
        if (request == null || !(request.value() instanceof java.util.Collection<?> values) || values.size() > 200)
            throw invalidOverride();
        List<DictionaryItemView> options;
        try { options = objectMapper.convertValue(request.value(), objectMapper.getTypeFactory().constructCollectionType(List.class, DictionaryItemView.class)); }
        catch (RuntimeException e) { throw invalidOverride(); }
        java.util.Set<String> allowed = items.findScoped(code, dataScopes.filter(resolve(auth), "dictionaries", auth))
                .stream().map(DictionaryItemEntity::value).collect(java.util.stream.Collectors.toSet());
        if (options.stream().anyMatch(i -> i.value() == null || !allowed.contains(i.value()) || i.label() == null || i.label().isBlank() || i.label().length() > 200 || i.sortOrder() < 0)
                || options.stream().map(DictionaryItemView::value).distinct().count() != options.size())
            throw invalidOverride();
        return overrides.save("dictionaries", code, organization, new com.abluepoint.summer.sample.configuration.ConfigurationOverrideRequest(options, request.publicReadable(), request.enabled(), request.expectedVersion()), auth, requestId);
    }

    private DictionaryTypeEntity scopedType(String code, Authentication authentication) {
        DataScopeService.ScopeFilter scope = dataScopes.filter(resolve(authentication), "dictionaries", authentication);
        return types.findScoped(code, scope)
                .orElseThrow(() -> new SummerException(com.abluepoint.summer.common.error.CommonErrorCode.NOT_FOUND));
    }

    private String snapshot(DictionaryTypeEntity type, String code) {
        try {
            return objectMapper.writeValueAsString(new DictionarySnapshot(type.name(), type.publicReadable(),
                    type.enabled(), items.findAllByTenantIdAndDictionaryCodeOrderBySortOrderAscValueAsc(type.tenantId(), code).stream()
                            .map(item -> new DictionaryItemView(item.value(), item.label(), item.sortOrder(), item.enabled())).toList()));
        } catch (JacksonException exception) {
            throw new IllegalStateException("Dictionary snapshot cannot be serialized", exception);
        }
    }

    private void evidence(String code, long version, String eventType, String requestId, String idempotencyKey, Authentication authentication) {
        try {
            String eventId = UUID.randomUUID().toString();
            String payload = objectMapper.writeValueAsString(new DictionaryEvent(eventId, resolve(authentication), code, eventType, version, requestId));
            outbox.saveAndFlush(new DictionaryOutboxEntity(eventId, resolve(authentication), code, eventType, version, requestId, idempotencyKey, payload));
            cache.invalidate(resolve(authentication), "dictionary");
            notifications.record(resolve(authentication), code, eventType, version, requestId);
            audit.recordSuccess(code, eventType, requestId, authentication);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Dictionary event cannot be serialized", exception);
        }
    }

    private DictionaryTypeVersionView replay(String code, DictionaryVersionRequest request, DictionaryTypeEntity type, String eventType) {
        String key = request == null ? null : request.idempotencyKey();
        if (key == null || key.isBlank()) return null;
        DictionaryOutboxEntity event = outbox.findByTenantIdAndDictionaryCodeAndIdempotencyKey(type.tenantId(), code, key).orElse(null);
        if (event == null) return null;
        if (!eventType.equals(event.eventType())) throw new SummerException(DictionaryErrorCode.CONFLICT);
        DictionaryVersionEntity version = versions.findByTenantIdAndDictionaryCodeAndVersion(type.tenantId(), code, event.version()).orElse(null);
        return view(type, version);
    }

    private static long currentVersion(DictionaryTypeEntity type) { return type.publishedVersion() == null ? 0 : type.publishedVersion(); }

    private static void requireExpected(DictionaryVersionRequest request, long current) {
        if (request == null || request.expectedVersion() == null || request.expectedVersion() != current) {
            throw new SummerException(DictionaryErrorCode.VERSION_CONFLICT);
        }
    }

    private static SummerException invalidOverride() {
        return new SummerException(DictionaryErrorCode.INVALID,
                List.of(new SummerException.Violation("value", "Override options must contain unique visible dictionary items with valid labels and sort order")));
    }

    private static SummerException invalidState(String message) {
        return new SummerException(DictionaryErrorCode.INVALID_STATE,
                List.of(new SummerException.Violation("state", message)));
    }

    private static String principal(Authentication authentication) {
        return authentication == null || authentication.getName() == null ? "system" : authentication.getName();
    }

    private static DictionaryTypeVersionView view(DictionaryTypeEntity type, DictionaryVersionEntity version) {
        long number = version == null ? currentVersion(type) : version.version();
        String status = type.published() ? "PUBLISHED" : "DISABLED";
        return new DictionaryTypeVersionView(type.code(), type.name(), number, status);
    }
}

record DictionarySnapshot(String name, boolean publicReadable, boolean enabled, List<DictionaryItemView> items) {}
record DictionaryEvent(String eventId, String tenantId, String dictionaryCode, String eventType,
                       long version, String requestId) {}
