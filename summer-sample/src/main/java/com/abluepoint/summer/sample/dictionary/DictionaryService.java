package com.abluepoint.summer.sample.dictionary;

import com.abluepoint.summer.security.authorization.ResourceAuthorization.Domain;
import com.abluepoint.summer.common.error.CommonErrorCode;
import com.abluepoint.summer.common.error.SummerException;
import com.abluepoint.summer.sample.cache.VersionedTwoLevelCache;
import com.abluepoint.summer.sample.authorization.BusinessDataScopes;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
class DictionaryService {
    @jakarta.persistence.PersistenceContext private jakarta.persistence.EntityManager entityManager;
    private final tools.jackson.databind.ObjectMapper objectMapper;
    private final DictionaryTypeRepository types;
    private final DictionaryItemRepository items;
    private final DictionaryGroups groups;
    private final DictionaryAuditService audit;
    private final VersionedTwoLevelCache cache;
    private final BusinessDataScopes dataScopes;

    private final com.abluepoint.summer.sample.configuration.ConfigurationActionAccess actions;

    DictionaryService(DictionaryTypeRepository types, DictionaryItemRepository items, DictionaryAuditService audit,
                      VersionedTwoLevelCache cache, BusinessDataScopes dataScopes, com.abluepoint.summer.sample.configuration.ConfigurationActionAccess actions, tools.jackson.databind.ObjectMapper objectMapper, DictionaryGroups groups) {
        this.groups = groups;
        this.objectMapper = objectMapper;
        this.types = types;
        this.actions = actions;
        this.items = items;
        this.audit = audit;
        this.cache = cache;
        this.dataScopes = dataScopes;
    }

    @Transactional(readOnly = true)
    List<DictionaryTypeView> list(Authentication authentication) {
        return dataScopes.read(Domain.platform().id(), "dictionaries", "read", authentication, scope -> {

        return types.findScoped(scope).stream().map(type -> view(type, scope)).toList();
            });
    }
    @Transactional(readOnly = true)
    List<DictionaryTypeView> list() { return list(null); }

    @Transactional
    DictionaryTypeView createType(DictionaryTypeRequest request, String requestId, Authentication authentication) {
        return dataScopes.write(Domain.platform().id(), "dictionaries", java.util.Set.of("create"), authentication, ranges -> {
        var scope = ranges.get("create");
        if (request != null && request.organizationId() != null) throw invalid("organizationId");
        validateCode(request == null ? null : request.code());
        String code = request.code().trim();
        if (types.findByTenantIdAndCode(Domain.platform().id(), code).isPresent()) {
            scopedType(code, scope);
            throw new SummerException(DictionaryErrorCode.CONFLICT);
        }
        if (bool(request.published())) {
            actions.require("dictionaries", "publish", authentication);
            throw new SummerException(DictionaryErrorCode.INVALID_STATE);
        }
        if (blank(request.name())) throw invalid("name");
        DictionaryTypeEntity type = new DictionaryTypeEntity(Domain.platform().id(), UUID.randomUUID().toString(), code, request.name().trim(),
                bool(request.published()), bool(request.publicReadable()), bool(request.enabled(), true), request.organizationId(), authentication == null ? null : authentication.getName(), authentication == null ? null : authentication.getName());
        try {
            DictionaryTypeView result = view(insertType(type, scope), scope);
            cache.invalidate(Domain.platform().id(), "dictionary");
            audit.recordSuccess(code, "DICTIONARY_CREATED", requestId, authentication);
            return result;
        } catch (DataIntegrityViolationException exception) {
            throw new SummerException(DictionaryErrorCode.CONFLICT, exception);
        }
            });
    }

    @Transactional
    DictionaryTypeView updateType(String code, DictionaryTypeRequest request, String requestId, Authentication authentication) {
        return dataScopes.write(Domain.platform().id(), "dictionaries", java.util.Set.of("update"), authentication, ranges -> {
        var scope = ranges.get("update");
        DictionaryTypeEntity type = lockedType(code, request == null ? null : request.expectedDraftRevision(), authentication, scope);
        if (request == null || blank(request.name())) throw invalid("name");
        if (request.organizationId() != null && !java.util.Objects.equals(request.organizationId(), type.organizationId())) throw invalid("organizationId");
        if (request.publicReadable() != null && request.publicReadable() != type.publicReadable()) actions.require("dictionaries", "publish", authentication);
        if (request.published() != null && request.published() != type.published()) throw new SummerException(DictionaryErrorCode.INVALID_STATE);
        if (request.enabled() != null && request.enabled() != type.enabled()) actions.require("dictionaries", request.enabled() ? "publish" : "disable", authentication);
        actions.require("dictionaries", "update", authentication);
        if (request.name().length() > 200) throw invalid("name");
        type = advance(type, request.expectedDraftRevision(), scope);
        type.change(request.name().trim(), bool(request.published(), type.published()),
                bool(request.publicReadable(), type.publicReadable()), bool(request.enabled(), type.enabled()));
        DictionaryTypeView result = view(saveType(type, scope), scope);
        ensureCapacity(type);
        cache.invalidate(Domain.platform().id(), "dictionary");
        audit.recordSuccess(code, "DICTIONARY_UPDATED", requestId, authentication);
        return result;
            });
    }

    @Transactional
    DictionaryItemView createItem(String code, DictionaryItemRequest request, String requestId, Authentication authentication) {
        return dataScopes.write(Domain.platform().id(), "dictionaries", java.util.Set.of("create", "update"), authentication, ranges -> {
        var scope = ranges.get("update");
        DictionaryTypeEntity type = lockedType(code, request == null ? null : request.expectedDraftRevision(), authentication, scope);
        if (type.published() && type.publishedVersion() == null) actions.require("dictionaries", "publish", authentication);
        actions.require("dictionaries", "create", authentication);
        validateItem(request);
        if (Boolean.FALSE.equals(request.enabled())) actions.require("dictionaries", "disable", authentication);
        if (items.findByTenantIdAndDictionaryCodeAndValue(Domain.platform().id(), code, (String) request.value()).isPresent()) throw new SummerException(DictionaryErrorCode.CONFLICT);
        DictionaryItemEntity item = new DictionaryItemEntity(type.tenantId(), UUID.randomUUID().toString(), type.code(), (String) request.value(),
                request.label().trim(), request.sortOrder(), bool(request.enabled(), true), type.organizationId(), authentication == null ? null : authentication.getName(), authentication == null ? null : authentication.getName());
        item.edit((String) request.value(), request.description());
        type = advance(type, request.expectedDraftRevision(), scope);
        DictionaryItemView result;
        try {
            result = view(insertItem(item,ranges.get("create")));
        } catch (DataIntegrityViolationException exception) {
            throw new SummerException(DictionaryErrorCode.CONFLICT, exception);
        }
        ensureCapacity(type);
        cache.invalidate(Domain.platform().id(), "dictionary");
        audit.recordSuccess(code, "DICTIONARY_ITEM_CREATED", requestId, authentication);
        return result;
            });
    }

    @Transactional
    DictionaryItemView updateItem(String code, String value, DictionaryItemRequest request, String requestId, Authentication authentication) {
        return dataScopes.write(Domain.platform().id(), "dictionaries", java.util.Set.of("update"), authentication, ranges -> {
        var scope = ranges.get("update");
        DictionaryTypeEntity type = lockedType(code, request == null ? null : request.expectedDraftRevision(), authentication, scope);
        if (type.published() && type.publishedVersion() == null) actions.require("dictionaries", "publish", authentication);
        if (request == null || blank(request.label()) || request.label().length() > 200 || (request.description() != null && request.description().length() > 2000) || request.sortOrder() == null || request.sortOrder() < 0) {
            throw invalid("item");
        }
        DictionaryItemEntity item = items.findScoped(code, value, scope)
                .orElseThrow(() -> new SummerException(CommonErrorCode.NOT_FOUND));
        if (request.enabled() != null && request.enabled() != item.enabled()) actions.require("dictionaries", request.enabled() ? "publish" : "disable", authentication);
        actions.require("dictionaries", "update", authentication);
        String nextValue = request.value() == null ? value : validValue(request.value());
        if (!value.equals(nextValue) && items.publishedIdentity(item.id()) == 1) throw new SummerException(DictionaryErrorCode.INVALID_STATE);
        entityManager.detach(item);
        item.edit(nextValue, request.description() == null ? item.description() : request.description());
        type = advance(type, request.expectedDraftRevision(), scope);
        item.change(request.label().trim(), request.sortOrder(), bool(request.enabled(), item.enabled()));
        DictionaryItemView result;
        try { result = view(saveItem(item,scope)); }
        catch (DataIntegrityViolationException exception) { throw new SummerException(DictionaryErrorCode.CONFLICT, exception); }
        ensureCapacity(type);
        cache.invalidate(Domain.platform().id(), "dictionary");
        audit.recordSuccess(code, "DICTIONARY_ITEM_UPDATED", requestId, authentication);
        return result;
            });
    }

    private DictionaryTypeEntity scopedType(String code, BusinessDataScopes.Filter scope) {
        return types.findScoped(code, scope).orElseThrow(() -> new SummerException(CommonErrorCode.NOT_FOUND));
    }

    private DictionaryTypeView view(DictionaryTypeEntity type, BusinessDataScopes.Filter scope) {
        List<DictionaryItemEntity> values = scope == null ? items.findAllByTenantIdAndDictionaryCodeOrderBySortOrderAscValueAsc(type.tenantId(), type.code()) : items.findScoped(type.code(), scope);
        long version = type.publishedVersion() == null ? 0 : type.publishedVersion();
        return new DictionaryTypeView(type.code(), type.name(), type.published(), type.publicReadable(), type.enabled(), values.stream().map(DictionaryService::view).toList(), version, type.publishedVersion(), type.tenantId(), type.organizationId(), type.draftRevision(), type.effectiveRevision());
    }

    private static DictionaryItemView view(DictionaryItemEntity item) {
        return new DictionaryItemView(item.value(), item.label(), item.sortOrder(), item.enabled(), item.description());
    }

    private static void validateCode(String code) {
        if (code == null || !code.matches("[a-z][a-z0-9-]{1,78}[a-z0-9]")) throw invalid("code");
    }

    private static void validateItem(DictionaryItemRequest request) {
        if (request == null || request.value() == null) throw invalid("value");
        validValue(request.value());
        if (blank(request.label()) || request.label().length() > 200 || (request.description() != null && request.description().length() > 2000) || request.sortOrder() == null || request.sortOrder() < 0) throw invalid("item");
    }

    private static String validValue(Object value) {
        if (!(value instanceof String text) || !text.matches("[a-z0-9](?:[a-z0-9._-]{0,78}[a-z0-9])?")) throw invalid("value");
        return text;
    }

    private DictionaryTypeEntity lockedType(String code, Object expected, Authentication auth, BusinessDataScopes.Filter scope) {
        scopedType(code, scope);
        DictionaryTypeEntity type = types.lock(Domain.platform().id(), code).orElseThrow(() -> new SummerException(CommonErrorCode.NOT_FOUND));
        entityManager.refresh(type, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        items.lockAll(type.tenantId(), code).forEach(item -> entityManager.refresh(item, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE));
        if (!(expected instanceof Integer || expected instanceof Long) || ((Number) expected).longValue() < 0) throw invalid("expectedDraftRevision");
        if (types.currentControl(type.tenantId(), code).getDraftRevision() != ((Number) expected).longValue()) throw new SummerException(DictionaryErrorCode.CONFLICT);
        return type;
    }

    private DictionaryTypeEntity advance(DictionaryTypeEntity type, Object expected, BusinessDataScopes.Filter scope) {
        return dataScopes.update(scope,DictionaryTypeEntity.class,type,id(type),"draftRevision",((Number)expected).longValue(),java.util.Map.of());
    }

    @Transactional(readOnly = true)
    DictionaryDraftView draft(String code, Authentication auth) {
        return dataScopes.read(Domain.platform().id(), "dictionaries", "read", auth, scope -> {
        DictionaryTypeEntity type = scopedType(code, scope);
        return draftView(type, scope);
            });
    }

    private DictionaryDraftView draftView(DictionaryTypeEntity type, BusinessDataScopes.Filter scope) {
        return draftView(type, scope, false);
    }

    private DictionaryDraftView draftView(DictionaryTypeEntity type, BusinessDataScopes.Filter scope, boolean currentIdentity) {
        var control = currentIdentity ? types.currentControl(type.tenantId(), type.code()) : null;
        var published = currentIdentity ? java.util.Set.copyOf(items.publishedIds(type.tenantId(), type.code())) : java.util.Set.<String>of();
        var values = items.findScoped(type.code(), scope);
        return new DictionaryDraftView(type.code(), type.name(), type.publicReadable(), type.enabled(), currentIdentity ? control.getDraftRevision() : type.draftRevision(),
                currentIdentity ? control.getPublishedVersion() : type.publishedVersion() == null ? 0 : type.publishedVersion(), currentIdentity ? control.getEffectiveRevision() : type.effectiveRevision(), values.stream().map(i -> new DictionaryDraftItem(i.id(), i.value(), i.label(), i.description(), i.enabled(), currentIdentity ? published.contains(i.id()) : i.everPublished(), java.math.BigDecimal.valueOf(i.sortOrder()))).toList(), values.stream().map(DictionaryItemEntity::id).toList(), groups.read(type, values.stream().map(DictionaryItemEntity::id).collect(java.util.stream.Collectors.toSet()), currentIdentity));
    }

    @Transactional
    DictionaryDraftView saveDraft(String code, DictionaryDraftRequest request, String requestId, Authentication auth) {
        return dataScopes.writeResolved(Domain.platform().id(), "dictionaries", () -> draftActions(code,request,auth), auth, ranges -> {
        var scope = ranges.get("update");
        DictionaryTypeEntity type = lockedType(code, request == null ? null : request.expectedDraftRevision(), auth, scope);
        var all = items.lockAll(type.tenantId(), code);
        var visible = items.findScoped(code, scope);
        if (visible.size() != all.size()) throw new org.springframework.security.access.AccessDeniedException("Full dictionary maintenance required");
        if (request.name() == null || request.name().isBlank() || request.name().length() > 200 || request.publicReadable() == null || request.enabled() == null || request.items() == null || request.itemOrder() == null) throw invalid("draft");
        var existing = all.stream().collect(java.util.stream.Collectors.toMap(DictionaryItemEntity::id, i -> i));
        var ids = new java.util.HashSet<String>(); var values = new java.util.HashSet<String>();
        for (var i : request.items()) {
            if (i == null || i.id() == null || i.id().isBlank() || !ids.add(i.id()) || !values.add(validValue(i.value())) || i.label() == null || i.label().isBlank() || i.label().length() > 200 || (i.description() != null && i.description().length() > 2000)) throw invalid("items");
            if (!existing.containsKey(i.id()) && !i.id().startsWith("new:")) throw invalid("items");
        }
        if (request.itemOrder().stream().anyMatch(java.util.Objects::isNull) || request.itemOrder().size() != ids.size() || !new java.util.HashSet<>(request.itemOrder()).equals(ids)) throw invalid("itemOrder");
        boolean legacyOrderChanged = !request.itemOrder().equals(all.stream().map(DictionaryItemEntity::id).toList());
        var sortOrders = new java.util.HashMap<String,Integer>();
        for (var item : request.items()) {
            var old = existing.get(item.id());
            int rank = !legacyOrderChanged && old != null ? old.sortOrder() : request.itemOrder().indexOf(item.id());
            if (item.sortOrder() != null) {
                try { rank = item.sortOrder().intValueExact(); } catch (ArithmeticException failure) { throw invalid("sortOrder"); }
                if (rank < 0) throw invalid("sortOrder");
            }
            sortOrders.put(item.id(), rank);
        }
        var byId = request.items().stream().collect(java.util.stream.Collectors.toMap(DictionaryDraftItem::id, i -> i));
        var itemOrder = request.itemOrder().stream().sorted(java.util.Comparator.comparingInt((String id) -> sortOrders.get(id))
                .thenComparing(id -> (String)byId.get(id).value())).toList();
        var desiredGroups = groups.prepare(type, request.groups(), itemOrder, auth);
        var oldGroups = groups.read(type, all.stream().map(DictionaryItemEntity::id).collect(java.util.stream.Collectors.toSet()), true);
        boolean orderChanged = !itemOrder.equals(all.stream().map(DictionaryItemEntity::id).toList());
        // Permission follows the relative order of retained identities, not storage renumbering.
        boolean reorderedExisting = !itemOrder.stream().filter(existing::containsKey).toList().equals(
                all.stream().map(DictionaryItemEntity::id).filter(ids::contains).toList());
        boolean unchanged = type.name().equals(request.name()) && type.publicReadable() == request.publicReadable() && type.enabled() == request.enabled() && all.size() == request.items().size();
        if (unchanged) for (var i : request.items()) {
            var old = existing.get(i.id());
            if (old == null || !old.value().equals(i.value()) || !old.label().equals(i.label()) || !old.description().equals(i.description() == null ? "" : i.description()) || old.enabled() != i.enabled() || old.sortOrder() != sortOrders.get(i.id()) || orderChanged) { unchanged = false; break; }
        }
        unchanged = unchanged && oldGroups.equals(desiredGroups);
        if (unchanged) { ensureCapacity(type); return draftView(type, scope, true); }
        if (!type.name().equals(request.name())) actions.require("dictionaries", "update", auth);
        if (type.publicReadable() != request.publicReadable()) actions.require("dictionaries", "publish", auth);
        if (type.enabled() != request.enabled()) actions.require("dictionaries", request.enabled() ? "publish" : "disable", auth);
        for (var old : all) if (!ids.contains(old.id())) {
            actions.require("dictionaries", "update", auth);
            if (items.publishedIdentity(old.id()) == 1) throw new SummerException(DictionaryErrorCode.INVALID_STATE);
            groups.removeItem(type, old.id(), ranges.get("delete"));
            dataScopes.delete(ranges.get("delete"), DictionaryItemEntity.class, old, old.id());
        }
        // Flush deletions before reusing a never-published value; transaction rollback covers failures.
        items.flush();
        var savedIds = new java.util.HashMap<String,String>();
        for (var i : request.items()) {
            var old = existing.get(i.id()); String value = (String) i.value(); int order = sortOrders.get(i.id());
            String description = i.description() == null ? "" : i.description();
            if (old == null) {
                actions.require("dictionaries", "create", auth);
                if (!i.enabled()) actions.require("dictionaries", "disable", auth);
                old = new DictionaryItemEntity(type.tenantId(), UUID.randomUUID().toString(), code, value, i.label(), order, i.enabled(), type.organizationId(), auth.getName(), auth.getName());
            } else {
                if (!old.value().equals(value) && items.publishedIdentity(old.id()) == 1) throw new SummerException(DictionaryErrorCode.INVALID_STATE);
                if (!old.value().equals(value) || !old.label().equals(i.label()) || !old.description().equals(description) || reorderedExisting || (i.sortOrder() != null && old.sortOrder() != order)) actions.require("dictionaries", "update", auth);
                if (old.enabled() != i.enabled()) actions.require("dictionaries", i.enabled() ? "publish" : "disable", auth);
            }
            entityManager.detach(old);
            old.edit(value, description); old.change(i.label(), order, i.enabled());
            if (existing.containsKey(i.id())) saveItem(old,scope); else insertItem(old,ranges.get("create"));
            savedIds.put(i.id(),old.id());
        }
        type = advance(type, request.expectedDraftRevision(), scope);
        type.change(request.name(), type.published(), request.publicReadable(), request.enabled());
        type = saveType(type,scope);
        try { items.flush(); types.flush(); }
        catch (DataIntegrityViolationException exception) { throw new SummerException(DictionaryErrorCode.CONFLICT, exception); }
        groups.save(type, desiredGroups, savedIds, ranges);
        ensureCapacity(type);
        audit.recordSuccess(code, "DICTIONARY_DRAFT_SAVED", requestId, auth);
        return draftView(type, scope, true);
            });
    }

    private java.util.Set<String> draftActions(String code, DictionaryDraftRequest request, Authentication auth) {
        var actions = new java.util.HashSet<String>(); actions.add("update");
        if (request == null || request.items() == null) return actions;
        var current = items.findAllByTenantIdAndDictionaryCodeOrderBySortOrderAscValueAsc(Domain.platform().id(),code);
        var ids = request.items().stream().filter(java.util.Objects::nonNull).map(DictionaryDraftItem::id).collect(java.util.stream.Collectors.toSet());
        if (request.items().stream().filter(java.util.Objects::nonNull).anyMatch(i -> i.id() != null && i.id().startsWith("new:"))) actions.add("create");
        if (current.stream().anyMatch(i -> !ids.contains(i.id()))) actions.add("delete");
        types.findByTenantIdAndCode(Domain.platform().id(),code).ifPresent(type -> actions.addAll(groups.mutationActions(type,request.groups())));
        return actions;
    }

    private Object id(DictionaryTypeEntity type) { return entityManager.getEntityManagerFactory().getPersistenceUnitUtil().getIdentifier(type); }
    private DictionaryTypeEntity insertType(DictionaryTypeEntity type, BusinessDataScopes.Filter scope) {
        var values = new java.util.LinkedHashMap<String,Object>();
        values.put("id",id(type)); values.put("tenant_id",type.tenantId()); values.put("organization_id",type.organizationId());
        values.put("created_by",type.createdBy());values.put("owner_id",type.ownerId());values.put("code",type.code());values.put("name",type.name());
        values.put("published",type.published());values.put("public_readable",type.publicReadable());values.put("enabled",type.enabled());
        values.put("published_version",type.publishedVersion());values.put("draft_revision",type.draftRevision());values.put("effective_revision",type.effectiveRevision());
        dataScopes.insert(scope,BusinessDataScopes.Table.DICTIONARY_TYPES,values);
        return types.findById((String)id(type)).orElseThrow();
    }
    private DictionaryTypeEntity saveType(DictionaryTypeEntity type, BusinessDataScopes.Filter scope) {
        var values = new java.util.LinkedHashMap<String,Object>();
        values.put("name",type.name());values.put("published",type.published());values.put("publicReadable",type.publicReadable());values.put("enabled",type.enabled());
        values.put("publishedVersion",type.publishedVersion());values.put("draftRevision",type.draftRevision());values.put("effectiveRevision",type.effectiveRevision());
        return dataScopes.replace(scope,DictionaryTypeEntity.class,type,id(type),values);
    }
    private DictionaryItemEntity insertItem(DictionaryItemEntity item, BusinessDataScopes.Filter scope) {
        var values = new java.util.LinkedHashMap<String,Object>();
        values.put("id",item.id());values.put("tenant_id",item.tenantId());values.put("organization_id",item.organizationId());
        values.put("created_by",item.createdBy());values.put("owner_id",item.ownerId());values.put("dictionary_code",item.dictionaryCode());
        values.put("item_value",item.value());values.put("label",item.label());values.put("sort_order",item.sortOrder());values.put("enabled",item.enabled());
        values.put("description",item.description());values.put("ever_published",item.everPublished());
        dataScopes.insert(scope,BusinessDataScopes.Table.DICTIONARY_ITEMS,values);
        return items.findById(item.id()).orElseThrow();
    }
    private DictionaryItemEntity saveItem(DictionaryItemEntity item, BusinessDataScopes.Filter scope) {
        return dataScopes.replace(scope,DictionaryItemEntity.class,item,item.id(),java.util.Map.of("value",item.value(),"label",item.label(),"sortOrder",item.sortOrder(),"enabled",item.enabled(),"description",item.description()));
    }

    private void ensureCapacity(DictionaryTypeEntity type) {
        DictionarySnapshot.encode(objectMapper, new DictionarySnapshot(type.name(), type.publicReadable(), type.enabled(),
                items.lockAll(type.tenantId(), type.code()).stream().map(DictionaryService::view).toList(), 2,
                groups.snapshot(type, items.lockAll(type.tenantId(), type.code()))));
    }

    private static SummerException invalid(String field) {
        return new SummerException(DictionaryErrorCode.INVALID,
                List.of(new SummerException.Violation(field, "is invalid")));
    }

    private static boolean bool(Boolean value) { return Boolean.TRUE.equals(value); }
    private static boolean bool(Boolean value, boolean fallback) { return value == null ? fallback : value; }
    private static boolean blank(String value) { return value == null || value.isBlank(); }
}
