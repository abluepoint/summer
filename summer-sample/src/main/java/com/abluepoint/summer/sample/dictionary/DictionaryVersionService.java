package com.abluepoint.summer.sample.dictionary;

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
    static final String TENANT_ID = "ent-acme";
    private final DictionaryTypeRepository types;
    private final DictionaryItemRepository items;
    private final DictionaryVersionRepository versions;
    private final DictionaryOutboxRepository outbox;
    private final DictionaryAuditService audit;
    private final VersionedTwoLevelCache cache;
    private final DataScopeService dataScopes;
    private final ObjectMapper objectMapper;
    private final ResilientReadService resilientReads;
    private final DictionaryNotificationService notifications;

    DictionaryVersionService(DictionaryTypeRepository types, DictionaryItemRepository items,
                             DictionaryVersionRepository versions, DictionaryOutboxRepository outbox,
                             DictionaryAuditService audit, VersionedTwoLevelCache cache,
                             DataScopeService dataScopes, ObjectMapper objectMapper,
                             ResilientReadService resilientReads, DictionaryNotificationService notifications) {
        this.types = types;
        this.items = items;
        this.versions = versions;
        this.outbox = outbox;
        this.audit = audit;
        this.cache = cache;
        this.dataScopes = dataScopes;
        this.objectMapper = objectMapper;
        this.resilientReads = resilientReads;
        this.notifications = notifications;
    }

    @Transactional
    DictionaryTypeVersionView publish(String code, DictionaryVersionRequest request, String requestId,
                                      Authentication authentication) {
        DictionaryTypeEntity type = scopedType(code, authentication);
        DictionaryTypeVersionView replay = replay(code, request, type, "DICTIONARY_PUBLISHED");
        if (replay != null) return replay;
        long current = currentVersion(type);
        requireExpected(request, current);
        long next = versions.findTopByTenantIdAndDictionaryCodeOrderByVersionDesc(TENANT_ID, code)
                .map(value -> value.version() + 1).orElse(1L);
        String snapshot = snapshot(type, code);
        DictionaryVersionEntity version = new DictionaryVersionEntity(UUID.randomUUID().toString(), TENANT_ID,
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
        return view(type, versions.findByTenantIdAndDictionaryCodeAndVersion(TENANT_ID, code, current).orElse(null));
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
                        TENANT_ID, code, request.targetVersion())
                .orElseThrow(() -> invalidState("Rollback target does not exist"));
        type.publish(target.version());
        types.saveAndFlush(type);
        evidence(code, target.version(), "DICTIONARY_ROLLED_BACK", requestId, request.idempotencyKey(), authentication);
        return view(type, target);
    }

    @Transactional(readOnly = true)
    List<DictionaryVersionView> list(String code, Authentication authentication) {
        scopedType(code, authentication);
        return versions.findAllByTenantIdAndDictionaryCodeOrderByVersionDesc(TENANT_ID, code).stream()
                .map(value -> new DictionaryVersionView(value.version(), value.status(), value.createdAt(), value.createdBy()))
                .toList();
    }

    @Transactional(readOnly = true)
    PublicDictionaryView publicRead(String code) {
        return resilientReads.read(ResilientReadService.DICTIONARIES, code, () -> cache.get(TENANT_ID, "dictionary", code, PublicDictionaryView.class, () -> {
            DictionaryTypeEntity type = types.findByCode(code)
                    .orElseThrow(() -> new SummerException(com.abluepoint.summer.common.error.CommonErrorCode.NOT_FOUND));
            if (!type.enabled() || !type.publicReadable() || !type.published()) {
                throw new SummerException(com.abluepoint.summer.common.error.CommonErrorCode.NOT_FOUND);
            }
            if (type.publishedVersion() == null) {
                List<DictionaryItemView> current = items.findAllByDictionaryCodeOrderBySortOrderAscValueAsc(code).stream()
                        .filter(DictionaryItemEntity::enabled)
                        .map(item -> new DictionaryItemView(item.value(), item.label(), item.sortOrder(), item.enabled()))
                        .toList();
                return new PublicDictionaryView(code, current, 0);
            }
            DictionaryVersionEntity version = versions.findByTenantIdAndDictionaryCodeAndVersion(
                            TENANT_ID, code, type.publishedVersion())
                    .orElseThrow(() -> new SummerException(com.abluepoint.summer.common.error.CommonErrorCode.NOT_FOUND));
            try {
                DictionarySnapshot snapshot = objectMapper.readValue(version.snapshotJson(), DictionarySnapshot.class);
                return new PublicDictionaryView(code, snapshot.items().stream().filter(DictionaryItemView::enabled).toList(), version.version());
            } catch (JacksonException exception) {
                throw new IllegalStateException("Dictionary snapshot cannot be read", exception);
            }
        }));
    }

    private DictionaryTypeEntity scopedType(String code, Authentication authentication) {
        DataScopeService.ScopeFilter scope = dataScopes.filter(TENANT_ID, "dictionaries", authentication);
        return types.findScoped(TENANT_ID, scope.tenant(), scope.operatorAnd(), scope.subject(), scope.self(),
                        scope.creator(), scope.owner(), scope.organization(), scope.organizationId()).stream()
                .filter(value -> value.code().equals(code)).findFirst()
                .orElseThrow(() -> new SummerException(com.abluepoint.summer.common.error.CommonErrorCode.NOT_FOUND));
    }

    private String snapshot(DictionaryTypeEntity type, String code) {
        try {
            return objectMapper.writeValueAsString(new DictionarySnapshot(type.name(), type.publicReadable(),
                    type.enabled(), items.findAllByDictionaryCodeOrderBySortOrderAscValueAsc(code).stream()
                            .map(item -> new DictionaryItemView(item.value(), item.label(), item.sortOrder(), item.enabled())).toList()));
        } catch (JacksonException exception) {
            throw new IllegalStateException("Dictionary snapshot cannot be serialized", exception);
        }
    }

    private void evidence(String code, long version, String eventType, String requestId, String idempotencyKey, Authentication authentication) {
        try {
            String eventId = UUID.randomUUID().toString();
            String payload = objectMapper.writeValueAsString(new DictionaryEvent(eventId, TENANT_ID, code, eventType, version, requestId));
            outbox.saveAndFlush(new DictionaryOutboxEntity(eventId, TENANT_ID, code, eventType, version, requestId, idempotencyKey, payload));
            cache.invalidate(TENANT_ID, "dictionary");
            notifications.record(code, eventType, version, requestId);
            audit.recordSuccess(code, eventType, requestId, authentication);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Dictionary event cannot be serialized", exception);
        }
    }

    private DictionaryTypeVersionView replay(String code, DictionaryVersionRequest request, DictionaryTypeEntity type, String eventType) {
        String key = request == null ? null : request.idempotencyKey();
        if (key == null || key.isBlank()) return null;
        DictionaryOutboxEntity event = outbox.findByTenantIdAndDictionaryCodeAndIdempotencyKey(TENANT_ID, code, key).orElse(null);
        if (event == null) return null;
        if (!eventType.equals(event.eventType())) throw new SummerException(DictionaryErrorCode.CONFLICT);
        DictionaryVersionEntity version = versions.findByTenantIdAndDictionaryCodeAndVersion(TENANT_ID, code, event.version()).orElse(null);
        return view(type, version);
    }

    private static long currentVersion(DictionaryTypeEntity type) { return type.publishedVersion() == null ? 0 : type.publishedVersion(); }

    private static void requireExpected(DictionaryVersionRequest request, long current) {
        if (request == null || request.expectedVersion() == null || request.expectedVersion() != current) {
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

    private static DictionaryTypeVersionView view(DictionaryTypeEntity type, DictionaryVersionEntity version) {
        long number = version == null ? currentVersion(type) : version.version();
        String status = type.published() ? "PUBLISHED" : "DISABLED";
        return new DictionaryTypeVersionView(type.code(), type.name(), number, status);
    }
}

record DictionarySnapshot(String name, boolean publicReadable, boolean enabled, List<DictionaryItemView> items) {}
record DictionaryEvent(String eventId, String tenantId, String dictionaryCode, String eventType,
                       long version, String requestId) {}
