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
    @jakarta.persistence.PersistenceContext private jakarta.persistence.EntityManager entityManager;
    private final DictionaryTypeRepository types;
    private final EffectiveDictionaryReader effective;
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
                             ResilientReadService resilientReads, DictionaryNotificationService notifications, com.abluepoint.summer.sample.configuration.ConfigurationOverrideService overrides, com.abluepoint.summer.sample.configuration.ConfigurationActionAccess actions, com.abluepoint.summer.sample.resilience.ConfigurationFallbackGuard fallbackGuard, EffectiveDictionaryReader effective) {
        this.effective = effective;
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
        actions.require("dictionaries", "publish", authentication);
        validateRequest(request);
        revision(request.expectedDraftRevision());
        DictionaryTypeEntity type = lockedType(code, authentication);
        DictionaryTypeVersionView replay = replay(code, request, type, "DICTIONARY_PUBLISHED", authentication);
        if (replay != null) return replay;
        requireExpected(request, type);
        if (request.expectedDraftRevision() == null || revision(request.expectedDraftRevision()) != type.draftRevision()) throw new SummerException(DictionaryErrorCode.VERSION_CONFLICT);
        claimEffective(type, request, true);
        long next = versions.findTopByTenantIdAndDictionaryCodeOrderByVersionDesc(resolve(authentication), code)
                .map(value -> value.version() + 1).orElse(1L);
        requireFullItems(type, authentication);
        String snapshot = snapshot(type, code);
        items.markPublished(type.tenantId(), code);
        DictionaryVersionEntity version = new DictionaryVersionEntity(UUID.randomUUID().toString(), resolve(authentication),
                code, next, "PUBLISHED", snapshot, principal(authentication), requestId);
        type.publish(next);

        versions.saveAndFlush(version);
        types.saveAndFlush(type);
        evidence(code, next, "DICTIONARY_PUBLISHED", requestId, request, type, authentication);
        return view(type, version);
    }

    @Transactional
    DictionaryTypeVersionView disable(String code, DictionaryVersionRequest request, String requestId,
                                      Authentication authentication) {
        actions.require("dictionaries", "disable", authentication);
        validateRequest(request);
        DictionaryTypeEntity type = lockedType(code, authentication);
        DictionaryTypeVersionView replay = replay(code, request, type, "DICTIONARY_DISABLED", authentication);
        if (replay != null) return replay;
        long current = currentVersion(type);
        requireExpected(request, type);
        if (type.publishedVersion() == null || !type.published()) {
            throw invalidState("Dictionary is not published");
        }
        claimEffective(type, request, false);
        type.disable();

        types.saveAndFlush(type);
        evidence(code, current, "DICTIONARY_DISABLED", requestId, request, type, authentication);
        return view(type, versions.findByTenantIdAndDictionaryCodeAndVersion(resolve(authentication), code, current).orElse(null));
    }

    @Transactional
    DictionaryTypeVersionView rollback(String code, DictionaryVersionRequest request, String requestId,
                                       Authentication authentication) {
        actions.require("dictionaries", "rollback", authentication);
        validateRequest(request);
        DictionaryTypeEntity type = lockedType(code, authentication);
        DictionaryTypeVersionView replay = replay(code, request, type, "DICTIONARY_ROLLED_BACK", authentication);
        if (replay != null) return replay;
        long current = currentVersion(type);
        requireExpected(request, type);
        if (request == null || request.targetVersion() == null || revision(request.targetVersion()) <= 0
                || revision(request.targetVersion()) == current) {
            throw invalidState("Rollback target is not a previous version");
        }
        DictionaryVersionEntity target = versions.findByTenantIdAndDictionaryCodeAndVersion(
                        resolve(authentication), code, revision(request.targetVersion()))
                .orElseThrow(() -> invalidState("Rollback target does not exist"));
        requireSnapshotVisible(type, target, authentication);
        claimEffective(type, request, false);
        type.publish(target.version());

        types.saveAndFlush(type);
        evidence(code, target.version(), "DICTIONARY_ROLLED_BACK", requestId, request, type, authentication);
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
    DictionaryHistoryView history(String code, long version, Authentication authentication) {
        actions.require("dictionaries", "read", authentication);
        var type = scopedType(code, authentication);
        var stored = versions.findByTenantIdAndDictionaryCodeAndVersion(type.tenantId(), code, version)
                .orElseThrow(() -> new SummerException(com.abluepoint.summer.common.error.CommonErrorCode.NOT_FOUND));
        requireSnapshotVisible(type, stored, authentication);
        return new DictionaryHistoryView(stored.version(), stored.status(), stored.createdAt(), stored.createdBy(),
                objectMapper.readValue(stored.snapshotJson(), DictionarySnapshot.class), type.draftRevision(), currentVersion(type), type.effectiveRevision());
    }

    @Transactional(readOnly = true)
    PublicDictionaryView publicRead(String code) { return publicRead(code, null); }

    @Transactional(readOnly = true)
    PublicDictionaryView publicRead(String code, Authentication authentication) { return publicRead(resolve(authentication), code, authentication); }

    @Transactional(readOnly = true)
    PublicDictionaryView publicRead(String tenant, String code, Authentication authentication) {
        actions.requirePublic(tenant, "dictionaries", authentication);
        var base = effective.read(tenant, code, authentication, true);
        var snapshot = base.snapshot();
        var allowedValues = base.visibleValues();
        var override = overrides.resolve(tenant, "dictionaries", code, authentication);
        if (override.isPresent()) {
            var selected = override.get();
            List<DictionaryItemView> options = objectMapper.readValue(selected.valueJson(), objectMapper.getTypeFactory().constructCollectionType(List.class, DictionaryItemView.class));
            var selectable = base.selectableValues();
            return new PublicDictionaryView(code, options.stream().filter(DictionaryItemView::enabled)
                    .filter(i -> selectable.contains(i.value())).toList(), selected.version(), base.publishedVersion(),
                    selected.publishedRevision(), "override", token(tenant, code, base.publishedVersion(), selected.sourceId() + ":" + selected.publishedRevision(), base), base.effectiveRevision());
        }
        String cacheKey = tenant + ":" + code + ":" + (authentication == null ? "" : authentication.getName()) + ":" + base.publishedVersion() + ":" + base.effectiveRevision() + ":" + base.scopeFingerprint() + ":" + new java.util.TreeSet<>(allowedValues);
        return resilientReads.read(ResilientReadService.DICTIONARIES, cacheKey, () -> cache.get(tenant, "dictionary", cacheKey, PublicDictionaryView.class,
                () -> new PublicDictionaryView(code, snapshot.items().stream().filter(DictionaryItemView::enabled)
                        .filter(i -> allowedValues.contains(i.value())).toList(), base.publishedVersion(), base.publishedVersion(), null,
                        "base", token(tenant, code, base.publishedVersion(), "base", base), base.effectiveRevision())),
                () -> fallbackGuard.dictionary(tenant, code, base.publishedVersion(), base.effectiveRevision(), base.scopeFingerprint(), allowedValues, authentication));
    }

    @Transactional(readOnly = true)
    EffectiveDictionaryView effectiveRead(String code, Authentication authentication) {
        var base = effective.read(resolve(authentication), code, authentication, false);
        var snapshot = base.snapshot();
        return new EffectiveDictionaryView(code, snapshot == null ? null : snapshot.name(), base.publishedVersion(),
                snapshot == null ? "UNPUBLISHED" : "PUBLISHED", snapshot == null ? false : snapshot.publicReadable(),
                snapshot == null ? false : snapshot.enabled(), snapshot == null ? List.of() : snapshot.items().stream()
                .filter(i -> base.visibleValues().contains(i.value())).toList(), snapshot == null ? List.of() : snapshot.groups(), base.effectiveRevision(), base.draftRevision());
    }

    @Transactional
    com.abluepoint.summer.sample.configuration.ConfigurationOverrideView override(String code, String organization,
            com.abluepoint.summer.sample.configuration.ConfigurationOverrideRequest request, Authentication auth, String requestId) {
        return overrides.save("dictionaries", code, organization, request, auth, requestId);
    }

    private DictionaryTypeEntity scopedType(String code, Authentication authentication) {
        DataScopeService.ScopeFilter scope = dataScopes.filter(resolve(authentication), "dictionaries", authentication);
        return types.findScoped(code, scope).orElseThrow(() -> new SummerException(com.abluepoint.summer.common.error.CommonErrorCode.NOT_FOUND));
    }

    private DictionaryTypeEntity lockedType(String code, Authentication auth) {
        scopedType(code, auth);
        var type = types.lock(resolve(auth), code).orElseThrow();
        entityManager.refresh(type, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        dataScopes.assertWrite(resolve(auth), "dictionaries", auth, type.organizationId());
        return type;
    }

    private void claimEffective(DictionaryTypeEntity type, DictionaryVersionRequest request, boolean publish) {
        if (types.claimEffective(type.tenantId(), type.code(), revision(request.expectedEffectiveRevision()),
                revision(request.expectedVersion()), publish ? revision(request.expectedDraftRevision()) : null) != 1)
            throw new SummerException(DictionaryErrorCode.VERSION_CONFLICT);
        entityManager.refresh(type, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
    }

    private List<DictionaryItemEntity> currentItems(DictionaryTypeEntity type) {
        var current = items.lockAll(type.tenantId(), type.code());
        current.forEach(item -> entityManager.refresh(item, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE));
        return current;
    }

    private void requireFullItems(DictionaryTypeEntity type, Authentication auth) {
        var all = currentItems(type);
        var visible = items.findScoped(type.code(), dataScopes.filter(type.tenantId(), "dictionaries", auth));
        if (all.size() != visible.size()) throw new org.springframework.security.access.AccessDeniedException("Full dictionary maintenance required");
    }

    private void requireSnapshotVisible(DictionaryTypeEntity type, DictionaryVersionEntity version, Authentication auth) {
        var allowed = items.findScoped(type.code(), dataScopes.filter(type.tenantId(), "dictionaries", auth)).stream()
                .map(DictionaryItemEntity::value).collect(java.util.stream.Collectors.toSet());
        if (objectMapper.readValue(version.snapshotJson(), DictionarySnapshot.class).items().stream().anyMatch(i -> !allowed.contains(i.value())))
            throw new org.springframework.security.access.AccessDeniedException("Dictionary history unavailable");
    }

    private String snapshot(DictionaryTypeEntity type, String code) {
        try {
            String json = DictionarySnapshot.encode(objectMapper, new DictionarySnapshot(type.name(), type.publicReadable(),
                    type.enabled(), currentItems(type).stream()
                            .map(item -> new DictionaryItemView(item.value(), item.label(), item.sortOrder(), item.enabled(), item.description())).toList()));
            return json;
        } catch (JacksonException exception) {
            throw new IllegalStateException("Dictionary snapshot cannot be serialized", exception);
        }
    }

    private void evidence(String code, long version, String eventType, String requestId, DictionaryVersionRequest request, DictionaryTypeEntity type, Authentication authentication) {
        try {
            String eventId = UUID.randomUUID().toString();
            String payload = objectMapper.writeValueAsString(new DictionaryEvent(eventId, resolve(authentication), code, eventType, version, requestId));
            var event = new DictionaryOutboxEntity(eventId, resolve(authentication), code, eventType, version, requestId, request.idempotencyKey(), payload);
            event.receipt(fingerprint(code, eventType, request, authentication), objectMapper.writeValueAsString(view(type,
                    versions.findByTenantIdAndDictionaryCodeAndVersion(type.tenantId(), code, version).orElse(null))));
            outbox.saveAndFlush(event);
            cache.invalidate(resolve(authentication), "dictionary");
            notifications.record(resolve(authentication), code, eventType, version, requestId);
            audit.recordSuccess(code, eventType, requestId, authentication);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Dictionary event cannot be serialized", exception);
        }
    }

    private String fingerprint(String code, String action, DictionaryVersionRequest request, Authentication auth) {
        String semantic = objectMapper.writeValueAsString(java.util.Arrays.asList(principal(auth), resolve(auth), code, action,
                request.expectedVersion(), request.expectedEffectiveRevision(), request.expectedDraftRevision(), request.targetVersion()));
        try { return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(semantic.getBytes(java.nio.charset.StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException exception) { throw new IllegalStateException(exception); }
    }

    private DictionaryTypeVersionView replay(String code, DictionaryVersionRequest request, DictionaryTypeEntity type, String eventType, Authentication auth) {
        String key = request == null ? null : request.idempotencyKey();
        if (key == null || key.isBlank()) return null;
        if (key.length() > 255) throw invalidState("Invalid idempotency key");
        DictionaryOutboxEntity event = outbox.findByTenantIdAndDictionaryCodeAndIdempotencyKey(type.tenantId(), code, key).orElse(null);
        if (event == null) return null;
        if (!eventType.equals(event.eventType()) || !fingerprint(code, eventType, request, auth).equals(event.requestFingerprint()) || event.resultJson() == null)
            throw new SummerException(DictionaryErrorCode.CONFLICT);
        var version = versions.current(type.tenantId(), code, event.version()).orElseThrow();
        requireSnapshotVisible(type, version, auth);
        return objectMapper.readValue(event.resultJson(), DictionaryTypeVersionView.class);
    }

    private static String token(String tenant, String code, long version, String source, EffectiveDictionaryReader.Effective base) {
        return UUID.nameUUIDFromBytes((tenant + ":" + code + ":" + version + ":" + base.effectiveRevision() + ":" + source + ":" + base.scopeFingerprint() + ":" + new java.util.TreeSet<>(base.visibleValues()))
                .getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();
    }

    private static long currentVersion(DictionaryTypeEntity type) { return type.publishedVersion() == null ? 0 : type.publishedVersion(); }

    private static long revision(Object value) {
        if (!(value instanceof Integer || value instanceof Long) || ((Number)value).longValue() < 0)
            throw new SummerException(DictionaryErrorCode.INVALID, List.of(new SummerException.Violation("revision", "must be a nonnegative integer")));
        return ((Number)value).longValue();
    }

    private static void validateRequest(DictionaryVersionRequest request) {
        if (request == null) throw new SummerException(DictionaryErrorCode.INVALID);
        revision(request.expectedVersion());
        revision(request.expectedEffectiveRevision());
        if (request.expectedDraftRevision() != null) revision(request.expectedDraftRevision());
        if (request.targetVersion() != null) revision(request.targetVersion());
        if (request.idempotencyKey() != null && (request.idempotencyKey().isBlank() || request.idempotencyKey().length() > 255))
            throw new SummerException(DictionaryErrorCode.INVALID);
    }

    private static void requireExpected(DictionaryVersionRequest request, DictionaryTypeEntity type) {
        if (request == null || request.expectedVersion() == null || revision(request.expectedVersion()) != currentVersion(type)
                || request.expectedEffectiveRevision() == null || revision(request.expectedEffectiveRevision()) != type.effectiveRevision()) {
            throw new SummerException(DictionaryErrorCode.VERSION_CONFLICT);
        }
    }

    private static SummerException invalidState(String message) {
        return new SummerException(DictionaryErrorCode.INVALID_STATE,
                List.of(new SummerException.Violation("state", message)));
    }

    private static String principal(Authentication authentication) {
        return authentication == null || authentication.getName() == null ? "system" : authentication.getName();
    }

    private DictionaryTypeVersionView view(DictionaryTypeEntity type, DictionaryVersionEntity version) {
        long number = version == null ? currentVersion(type) : version.version();
        String status = type.published() ? "PUBLISHED" : "DISABLED";
        return new DictionaryTypeVersionView(type.code(), version == null ? null : objectMapper.readValue(version.snapshotJson(), DictionarySnapshot.class).name(), number, status, type.draftRevision(), currentVersion(type), type.effectiveRevision());
    }
}

record DictionarySnapshot(String name, boolean publicReadable, boolean enabled, List<DictionaryItemView> items,
                          Integer formatVersion, List<Object> groups) {
    static final int MAX_BYTES = 512 * 1024;
    static String encode(ObjectMapper mapper, DictionarySnapshot snapshot) {
        String json = mapper.writeValueAsString(snapshot);
        if (json.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > MAX_BYTES)
            throw new SummerException(DictionaryErrorCode.INVALID, List.of(new SummerException.Violation("snapshot", "must not exceed 512 KiB UTF-8")));
        return json;
    }
    DictionarySnapshot {
        items = items == null ? List.of() : List.copyOf(items);
        groups = groups == null ? List.of() : List.copyOf(groups);
    }
    DictionarySnapshot(String name, boolean publicReadable, boolean enabled, List<DictionaryItemView> items) {
        this(name, publicReadable, enabled, items, 1, List.of());
    }
}
record EffectiveDictionaryView(String code, String name, long publishedVersion, String status,
                               boolean publicReadable, boolean enabled, List<DictionaryItemView> items, List<Object> groups, long effectiveRevision, long draftRevision) {}
record DictionaryEvent(String eventId, String tenantId, String dictionaryCode, String eventType,
                       long version, String requestId) {}

record DictionaryHistoryView(long version, String status, java.time.Instant createdAt, String createdBy, DictionarySnapshot snapshot, long draftRevision, long publishedVersion, long effectiveRevision) {}
