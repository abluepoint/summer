package com.abluepoint.summer.sample.dictionary;

import com.abluepoint.summer.security.authorization.ResourceAuthorization.Domain;
import com.abluepoint.summer.common.error.SummerException;
import com.abluepoint.summer.sample.authorization.BusinessDataScopes;
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
    private final DictionaryGroups groups;
    private final DictionaryVersionRepository versions;
    private final DictionaryOutboxRepository outbox;
    private final DictionaryAuditService audit;
    private final VersionedTwoLevelCache cache;
    private final BusinessDataScopes reads;
    private final ObjectMapper objectMapper;
    private final ResilientReadService resilientReads;
    private final com.abluepoint.summer.sample.resilience.ConfigurationFallbackGuard fallbackGuard;
    private final DictionaryNotificationService notifications;
    private final com.abluepoint.summer.sample.configuration.ConfigurationActionAccess actions;

    DictionaryVersionService(DictionaryTypeRepository types, DictionaryItemRepository items,
                             DictionaryVersionRepository versions, DictionaryOutboxRepository outbox,
                             DictionaryAuditService audit, VersionedTwoLevelCache cache,
                             ObjectMapper objectMapper,
                             ResilientReadService resilientReads, DictionaryNotificationService notifications, com.abluepoint.summer.sample.configuration.ConfigurationActionAccess actions, com.abluepoint.summer.sample.resilience.ConfigurationFallbackGuard fallbackGuard, EffectiveDictionaryReader effective, DictionaryGroups groups, BusinessDataScopes reads) {
        this.reads = reads;
        this.groups = groups;
        this.effective = effective;
        this.types = types;
        this.items = items;
        this.versions = versions;
        this.outbox = outbox;
        this.audit = audit;
        this.cache = cache;
        this.objectMapper = objectMapper;
        this.resilientReads = resilientReads;
        this.fallbackGuard = fallbackGuard;
        this.notifications = notifications;
        this.actions = actions;
    }

    @Transactional
    DictionaryTypeVersionView publish(String code, DictionaryVersionRequest request, String requestId,
                                      Authentication authentication) {
        actions.require("dictionaries", "publish", authentication);
        validateRequest(request);
        return reads.write(Domain.platform().id(),"dictionaries",java.util.Set.of("update"),authentication,ranges -> {
        var scope = ranges.get("update");
        revision(request.expectedDraftRevision());
        DictionaryTypeEntity type = lockedType(code, authentication, scope);
        DictionaryTypeVersionView replay = replay(code, request, type, "DICTIONARY_PUBLISHED", authentication, scope);
        if (replay != null) return replay;
        requireExpected(request, type);
        if (request.expectedDraftRevision() == null || revision(request.expectedDraftRevision()) != type.draftRevision()) throw new SummerException(DictionaryErrorCode.VERSION_CONFLICT);

        long next = versions.findTopByTenantIdAndDictionaryCodeOrderByVersionDesc(Domain.platform().id(), code)
                .map(value -> value.version() + 1).orElse(1L);
        requireFullItems(type, scope);
        String snapshot = snapshot(type, code);
        for (var item : currentItems(type)) {
            reads.replace(scope, DictionaryItemEntity.class,item,item.id(),java.util.Map.of("everPublished",true));
        }
        groups.markPublished(type,scope);
        DictionaryVersionEntity version = new DictionaryVersionEntity(UUID.randomUUID().toString(), Domain.platform().id(),
                code, next, "PUBLISHED", snapshot, principal(authentication), requestId);
        entityManager.detach(type);
        type.publish(next);

        version = persistVersion(type,version,scope);
        type = claimEffective(type, request, true,scope);
        evidence(code, next, "DICTIONARY_PUBLISHED", requestId, request, type, authentication);
        return view(type, version);
        });
    }

    @Transactional
    DictionaryTypeVersionView disable(String code, DictionaryVersionRequest request, String requestId,
                                      Authentication authentication) {
        actions.require("dictionaries", "disable", authentication);
        validateRequest(request);
        return reads.write(Domain.platform().id(),"dictionaries",java.util.Set.of("update"),authentication,ranges -> {
        var scope = ranges.get("update");
        DictionaryTypeEntity type = lockedType(code, authentication, scope);
        DictionaryTypeVersionView replay = replay(code, request, type, "DICTIONARY_DISABLED", authentication, scope);
        if (replay != null) return replay;
        long current = currentVersion(type);
        requireExpected(request, type);
        if (type.publishedVersion() == null || !type.published()) {
            throw invalidState("Dictionary is not published");
        }

        entityManager.detach(type);
        type.disable();

        type = claimEffective(type, request, false,scope);
        evidence(code, current, "DICTIONARY_DISABLED", requestId, request, type, authentication);
        return view(type, versions.findByTenantIdAndDictionaryCodeAndVersion(Domain.platform().id(), code, current).orElse(null));
        });
    }

    @Transactional
    DictionaryTypeVersionView rollback(String code, DictionaryVersionRequest request, String requestId,
                                       Authentication authentication) {
        actions.require("dictionaries", "rollback", authentication);
        validateRequest(request);
        return reads.write(Domain.platform().id(),"dictionaries",java.util.Set.of("update"),authentication,ranges -> {
        var scope = ranges.get("update");
        DictionaryTypeEntity type = lockedType(code, authentication, scope);
        DictionaryTypeVersionView replay = replay(code, request, type, "DICTIONARY_ROLLED_BACK", authentication, scope);
        if (replay != null) return replay;
        long current = currentVersion(type);
        requireExpected(request, type);
        if (request == null || request.targetVersion() == null || revision(request.targetVersion()) <= 0
                || revision(request.targetVersion()) == current) {
            throw invalidState("Rollback target is not a previous version");
        }
        DictionaryVersionEntity target = versions.findByTenantIdAndDictionaryCodeAndVersion(
                        Domain.platform().id(), code, revision(request.targetVersion()))
                .orElseThrow(() -> invalidState("Rollback target does not exist"));
        requireSnapshotVisible(type, target, scope);

        entityManager.detach(type);
        type.publish(target.version());

        type = claimEffective(type, request, false,scope);
        evidence(code, target.version(), "DICTIONARY_ROLLED_BACK", requestId, request, type, authentication);
        return view(type, target);
        });
    }

    @Transactional(readOnly = true)
    List<DictionaryVersionView> list(String code, Authentication authentication) {
        return reads.read(Domain.platform().id(),"dictionaries","read",authentication,scope -> {
        scopedType(code, authentication, scope);
        return versions.findAllByTenantIdAndDictionaryCodeOrderByVersionDesc(Domain.platform().id(), code).stream()
                .map(value -> new DictionaryVersionView(value.version(), value.status(), value.createdAt(), value.createdBy()))
                .toList();
        });
    }

    @Transactional(readOnly = true)
    DictionaryHistoryView history(String code, long version, Authentication authentication) {
        return reads.read(Domain.platform().id(),"dictionaries","read",authentication,scope -> {
        actions.require("dictionaries", "read", authentication);
        var type = scopedType(code, authentication, scope);
        var stored = versions.findByTenantIdAndDictionaryCodeAndVersion(type.tenantId(), code, version)
                .orElseThrow(() -> new SummerException(com.abluepoint.summer.common.error.CommonErrorCode.NOT_FOUND));
        requireSnapshotVisible(type, stored, scope);
        return new DictionaryHistoryView(stored.version(), stored.status(), stored.createdAt(), stored.createdBy(),
                objectMapper.readValue(stored.snapshotJson(), DictionarySnapshot.class), type.draftRevision(), currentVersion(type), type.effectiveRevision());
        });
    }

    @Transactional(readOnly = true)
    PublicDictionaryView publicRead(String code) { return publicRead(code, null); }

    @Transactional(readOnly = true)
    PublicDictionaryView publicRead(String code, Authentication authentication) {
        return publicRead(code, authentication, () -> actions.requirePublic("dictionaries", authentication));
    }

    @Transactional(readOnly = true)
    PublicDictionaryView publishedRead(String code, Authentication authentication) {
        return publicRead(code, authentication, () -> actions.requirePublishedDictionary(authentication));
    }

    private PublicDictionaryView publicRead(String code, Authentication authentication, Runnable authorize) {
        String tenant = Domain.platform().id();
        return reads.read(tenant,"dictionaries","read",authentication,scope -> {
        authorize.run();
        var base = effective.read(tenant, code, true, scope);
        var snapshot = base.snapshot();
        var allowedValues = base.visibleValues();
        String cacheKey = tenant + ":" + code + ":" + (authentication == null ? "" : authentication.getName()) + ":" + base.publishedVersion() + ":" + base.effectiveRevision() + ":" + base.scopeFingerprint() + ":" + new java.util.TreeSet<>(allowedValues);
        return resilientReads.read(ResilientReadService.DICTIONARIES, cacheKey, () -> cache.get(tenant, "dictionary", cacheKey, PublicDictionaryView.class,
                () -> new PublicDictionaryView(code, snapshot.items().stream().filter(DictionaryItemView::enabled)
                        .filter(i -> allowedValues.contains(i.value())).toList(), base.publishedVersion(), base.publishedVersion(), null,
                        "base", token(tenant, code, base.publishedVersion(), "base", base), base.effectiveRevision())),
                () -> fallbackGuard.dictionary(tenant, code, base.publishedVersion(), base.effectiveRevision(), base.scopeFingerprint(), allowedValues, authentication, authorize));
            });
    }

    @Transactional(readOnly = true)
    EffectiveDictionaryView effectiveRead(String code, Authentication authentication) {
        var base = effective.read(Domain.platform().id(), code, authentication, false);
        var snapshot = base.snapshot();
        return new EffectiveDictionaryView(code, snapshot == null ? null : snapshot.name(), base.publishedVersion(),
                snapshot == null ? "UNPUBLISHED" : "PUBLISHED", snapshot == null ? false : snapshot.publicReadable(),
                snapshot == null ? false : snapshot.enabled(), snapshot == null ? List.of() : snapshot.items().stream()
                .filter(i -> base.visibleValues().contains(i.value())).toList(), snapshot == null ? List.of() : snapshot.groups().stream().map(g -> new DictionarySnapshotGroup(g.id(), g.code(), g.name(), g.enabled(), g.memberValues().stream().filter(base.visibleValues()::contains).toList(),g.memberSortOrders())).toList(), base.effectiveRevision(), base.draftRevision());
    }

    private DictionaryTypeEntity scopedType(String code, Authentication authentication, BusinessDataScopes.Filter scope) {
        return types.findScoped(code, scope).orElseThrow(() -> new SummerException(com.abluepoint.summer.common.error.CommonErrorCode.NOT_FOUND));
    }

    private DictionaryTypeEntity lockedType(String code, Authentication auth, BusinessDataScopes.Filter scope) {
        scopedType(code, auth, scope);
        var type = types.lock(Domain.platform().id(), code).orElseThrow();
        entityManager.refresh(type, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        if (types.findScoped(code,scope).isEmpty()) throw new SummerException(com.abluepoint.summer.common.error.CommonErrorCode.NOT_FOUND);
        return type;
    }

    private DictionaryTypeEntity claimEffective(DictionaryTypeEntity type, DictionaryVersionRequest request,
            boolean publish, BusinessDataScopes.Filter scope) {
        var expected = new java.util.HashMap<String,Object>();
        expected.put("effectiveRevision",revision(request.expectedEffectiveRevision()));
        expected.put("publishedVersion",revision(request.expectedVersion()) == 0 ? null : revision(request.expectedVersion()));
        if (publish) expected.put("draftRevision",revision(request.expectedDraftRevision()));
        var updated = reads.replaceChecked(scope,DictionaryTypeEntity.class,type,id(type),expected,
                java.util.Map.of("effectiveRevision",revision(request.expectedEffectiveRevision())+1,
                    "published",type.published(),"publishedVersion",type.publishedVersion()));
        if (updated == null) throw new SummerException(DictionaryErrorCode.VERSION_CONFLICT);
        return updated;
    }

    private String id(DictionaryTypeEntity type) {
        return (String) entityManager.getEntityManagerFactory().getPersistenceUnitUtil().getIdentifier(type);
    }
    private DictionaryVersionEntity persistVersion(DictionaryTypeEntity type, DictionaryVersionEntity version, BusinessDataScopes.Filter scope) {
        var values = new java.util.HashMap<String,Object>();
        values.put("version_id",version.id());values.put("tenant_id",version.tenantId());values.put("dictionary_code",version.dictionaryCode());
        values.put("version_number",version.version());values.put("status",version.status());values.put("snapshot_json",version.snapshotJson());
        values.put("created_by",version.createdBy());values.put("request_id",version.requestId());values.put("created_at",version.createdAt());
        reads.insertDictionaryVersion(scope,id(type),values);
        return versions.findById(version.id()).orElseThrow();
    }

    private List<DictionaryItemEntity> currentItems(DictionaryTypeEntity type) {
        var current = items.lockAll(type.tenantId(), type.code());
        current.forEach(item -> entityManager.refresh(item, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE));
        return current;
    }

    private void requireFullItems(DictionaryTypeEntity type, BusinessDataScopes.Filter scope) {
        var all = currentItems(type);
        var visible = items.findScoped(type.code(), scope);
        if (all.size() != visible.size()) throw new org.springframework.security.access.AccessDeniedException("Full dictionary maintenance required");
    }

    private void requireSnapshotVisible(DictionaryTypeEntity type, DictionaryVersionEntity version, BusinessDataScopes.Filter scope) {
        var allowed = items.findScoped(type.code(), scope).stream()
                .map(DictionaryItemEntity::value).collect(java.util.stream.Collectors.toSet());
        if (objectMapper.readValue(version.snapshotJson(), DictionarySnapshot.class).items().stream().anyMatch(i -> !allowed.contains(i.value())))
            throw new org.springframework.security.access.AccessDeniedException("Dictionary history unavailable");
    }

    private String snapshot(DictionaryTypeEntity type, String code) {
        try {
            String json = DictionarySnapshot.encode(objectMapper, new DictionarySnapshot(type.name(), type.publicReadable(),
                    type.enabled(), currentItems(type).stream()
                            .map(item -> new DictionaryItemView(item.value(), item.label(), item.sortOrder(), item.enabled(), item.description())).toList(), 2, groups.snapshot(type, currentItems(type))));
            return json;
        } catch (JacksonException exception) {
            throw new IllegalStateException("Dictionary snapshot cannot be serialized", exception);
        }
    }

    private void evidence(String code, long version, String eventType, String requestId, DictionaryVersionRequest request, DictionaryTypeEntity type, Authentication authentication) {
        try {
            String eventId = UUID.randomUUID().toString();
            String payload = objectMapper.writeValueAsString(new DictionaryEvent(eventId, Domain.platform().id(), code, eventType, version, requestId));
            var event = new DictionaryOutboxEntity(eventId, Domain.platform().id(), code, eventType, version, requestId, request.idempotencyKey(), payload);
            event.receipt(fingerprint(code, eventType, request, authentication), objectMapper.writeValueAsString(view(type,
                    versions.findByTenantIdAndDictionaryCodeAndVersion(type.tenantId(), code, version).orElse(null))));
            outbox.saveAndFlush(event);
            cache.invalidate(Domain.platform().id(), "dictionary");
            notifications.record(Domain.platform().id(), code, eventType, version, requestId);
            audit.recordSuccess(code, eventType, requestId, authentication);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Dictionary event cannot be serialized", exception);
        }
    }

    private String fingerprint(String code, String action, DictionaryVersionRequest request, Authentication auth) {
        String semantic = objectMapper.writeValueAsString(java.util.Arrays.asList(principal(auth), Domain.platform().id(), code, action,
                request.expectedVersion(), request.expectedEffectiveRevision(), request.expectedDraftRevision(), request.targetVersion()));
        try { return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(semantic.getBytes(java.nio.charset.StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException exception) { throw new IllegalStateException(exception); }
    }

    private DictionaryTypeVersionView replay(String code, DictionaryVersionRequest request, DictionaryTypeEntity type, String eventType, Authentication auth, BusinessDataScopes.Filter scope) {
        String key = request == null ? null : request.idempotencyKey();
        if (key == null || key.isBlank()) return null;
        if (key.length() > 255) throw invalidState("Invalid idempotency key");
        DictionaryOutboxEntity event = outbox.findByTenantIdAndDictionaryCodeAndIdempotencyKey(type.tenantId(), code, key).orElse(null);
        if (event == null) return null;
        if (!eventType.equals(event.eventType()) || !fingerprint(code, eventType, request, auth).equals(event.requestFingerprint()) || event.resultJson() == null)
            throw new SummerException(DictionaryErrorCode.CONFLICT);
        var version = versions.current(type.tenantId(), code, event.version()).orElseThrow();
        requireSnapshotVisible(type, version, scope);
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
                          Integer formatVersion, List<DictionarySnapshotGroup> groups) {
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
                               boolean publicReadable, boolean enabled, List<DictionaryItemView> items, List<DictionarySnapshotGroup> groups, long effectiveRevision, long draftRevision) {}
record DictionaryEvent(String eventId, String tenantId, String dictionaryCode, String eventType,
                       long version, String requestId) {}

record DictionaryHistoryView(long version, String status, java.time.Instant createdAt, String createdBy, DictionarySnapshot snapshot, long draftRevision, long publishedVersion, long effectiveRevision) {}
