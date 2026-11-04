package com.abluepoint.summer.sample.dictionary;

import static com.abluepoint.summer.sample.configuration.ConfigurationTenantContext.resolve;
import com.abluepoint.summer.common.error.CommonErrorCode;
import com.abluepoint.summer.common.error.SummerException;
import com.abluepoint.summer.sample.cache.VersionedTwoLevelCache;
import com.abluepoint.summer.sample.authorization.DataScopeService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
class DictionaryService {
    @jakarta.persistence.PersistenceContext private jakarta.persistence.EntityManager entityManager;
    private final DictionaryTypeRepository types;
    private final DictionaryItemRepository items;
    private final DictionaryAuditService audit;
    private final VersionedTwoLevelCache cache;
    private final DataScopeService dataScopes;

    private final com.abluepoint.summer.sample.configuration.ConfigurationActionAccess actions;

    DictionaryService(DictionaryTypeRepository types, DictionaryItemRepository items, DictionaryAuditService audit,
                      VersionedTwoLevelCache cache, DataScopeService dataScopes, com.abluepoint.summer.sample.configuration.ConfigurationActionAccess actions) {
        this.types = types;
        this.actions = actions;
        this.items = items;
        this.audit = audit;
        this.cache = cache;
        this.dataScopes = dataScopes;
    }

    @Transactional(readOnly = true)
    List<DictionaryTypeView> list(Authentication authentication) {
        DataScopeService.ScopeFilter scope = dataScopes.filter(resolve(authentication), "dictionaries", authentication);
        return types.findScoped(scope).stream().map(type -> view(type, scope)).toList();
    }
    @Transactional(readOnly = true)
    List<DictionaryTypeView> list() { return list(null); }

    @Transactional
    DictionaryTypeView createType(DictionaryTypeRequest request, String requestId, Authentication authentication) {
        dataScopes.assertWrite(resolve(authentication), "dictionaries", authentication, request == null ? null : request.organizationId());
        validateCode(request == null ? null : request.code());
        String code = request.code().trim();
        if (types.findByTenantIdAndCode(resolve(authentication), code).isPresent()) {
            scopedType(code, authentication);
            throw new SummerException(DictionaryErrorCode.CONFLICT);
        }
        if (bool(request.published())) {
            actions.require("dictionaries", "publish", authentication);
            throw new SummerException(DictionaryErrorCode.INVALID_STATE);
        }
        if (blank(request.name())) throw invalid("name");
        DictionaryTypeEntity type = new DictionaryTypeEntity(resolve(authentication), UUID.randomUUID().toString(), code, request.name().trim(),
                bool(request.published()), bool(request.publicReadable()), bool(request.enabled(), true), request.organizationId(), authentication == null ? null : authentication.getName(), authentication == null ? null : authentication.getName());
        try {
            DictionaryTypeView result = view(types.saveAndFlush(type), dataScopes.filter(resolve(authentication), "dictionaries", authentication));
            cache.invalidate(resolve(authentication), "dictionary");
            audit.recordSuccess(code, "DICTIONARY_CREATED", requestId, authentication);
            return result;
        } catch (DataIntegrityViolationException exception) {
            throw new SummerException(DictionaryErrorCode.CONFLICT, exception);
        }
    }

    @Transactional
    DictionaryTypeView updateType(String code, DictionaryTypeRequest request, String requestId, Authentication authentication) {
        DictionaryTypeEntity type = lockedType(code, request == null ? null : request.expectedDraftRevision(), authentication);
        if (request == null || blank(request.name())) throw invalid("name");
        if (request.organizationId() != null && !java.util.Objects.equals(request.organizationId(), type.organizationId())) throw invalid("organizationId");
        if (request.publicReadable() != null && request.publicReadable() != type.publicReadable()) actions.require("dictionaries", "publish", authentication);
        if (request.published() != null && request.published() != type.published()) throw new SummerException(DictionaryErrorCode.INVALID_STATE);
        if (request.enabled() != null && request.enabled() != type.enabled()) actions.require("dictionaries", request.enabled() ? "publish" : "disable", authentication);
        actions.require("dictionaries", "update", authentication);
        if (request.name().length() > 200) throw invalid("name");
        advance(type, request.expectedDraftRevision());
        type.change(request.name().trim(), bool(request.published(), type.published()),
                bool(request.publicReadable(), type.publicReadable()), bool(request.enabled(), type.enabled()));
        DictionaryTypeView result = view(types.saveAndFlush(type), dataScopes.filter(resolve(authentication), "dictionaries", authentication));
        cache.invalidate(resolve(authentication), "dictionary");
        audit.recordSuccess(code, "DICTIONARY_UPDATED", requestId, authentication);
        return result;
    }

    @Transactional
    DictionaryItemView createItem(String code, DictionaryItemRequest request, String requestId, Authentication authentication) {
        DictionaryTypeEntity type = lockedType(code, request == null ? null : request.expectedDraftRevision(), authentication);
        if (type.published() && type.publishedVersion() == null) actions.require("dictionaries", "publish", authentication);
        dataScopes.assertWrite(resolve(authentication), "dictionaries", authentication, type.organizationId());
        actions.require("dictionaries", "create", authentication);
        validateItem(request);
        if (Boolean.FALSE.equals(request.enabled())) actions.require("dictionaries", "disable", authentication);
        if (items.findByTenantIdAndDictionaryCodeAndValue(resolve(authentication), code, (String) request.value()).isPresent()) throw new SummerException(DictionaryErrorCode.CONFLICT);
        DictionaryItemEntity item = new DictionaryItemEntity(type.tenantId(), UUID.randomUUID().toString(), type.code(), (String) request.value(),
                request.label().trim(), request.sortOrder(), bool(request.enabled(), true), type.organizationId(), authentication == null ? null : authentication.getName(), authentication == null ? null : authentication.getName());
        item.edit((String) request.value(), request.description());
        advance(type, request.expectedDraftRevision());
        DictionaryItemView result;
        try {
            result = view(items.saveAndFlush(item));
        } catch (DataIntegrityViolationException exception) {
            throw new SummerException(DictionaryErrorCode.CONFLICT, exception);
        }
        cache.invalidate(resolve(authentication), "dictionary");
        audit.recordSuccess(code, "DICTIONARY_ITEM_CREATED", requestId, authentication);
        return result;
    }

    @Transactional
    DictionaryItemView updateItem(String code, String value, DictionaryItemRequest request, String requestId, Authentication authentication) {
        DictionaryTypeEntity type = lockedType(code, request == null ? null : request.expectedDraftRevision(), authentication);
        if (type.published() && type.publishedVersion() == null) actions.require("dictionaries", "publish", authentication);
        if (request == null || blank(request.label()) || request.label().length() > 200 || (request.description() != null && request.description().length() > 2000) || request.sortOrder() == null || request.sortOrder() < 0) {
            throw invalid("item");
        }
        DictionaryItemEntity item = items.findScoped(code, value, dataScopes.filter(resolve(authentication), "dictionaries", authentication))
                .orElseThrow(() -> new SummerException(CommonErrorCode.NOT_FOUND));
        if (request.enabled() != null && request.enabled() != item.enabled()) actions.require("dictionaries", request.enabled() ? "publish" : "disable", authentication);
        actions.require("dictionaries", "update", authentication);
        String nextValue = request.value() == null ? value : validValue(request.value());
        if (!value.equals(nextValue) && item.everPublished()) throw new SummerException(DictionaryErrorCode.INVALID_STATE);
        item.edit(nextValue, request.description() == null ? item.description() : request.description());
        advance(type, request.expectedDraftRevision());
        item.change(request.label().trim(), request.sortOrder(), bool(request.enabled(), item.enabled()));
        DictionaryItemView result;
        try { result = view(items.saveAndFlush(item)); }
        catch (DataIntegrityViolationException exception) { throw new SummerException(DictionaryErrorCode.CONFLICT, exception); }
        cache.invalidate(resolve(authentication), "dictionary");
        audit.recordSuccess(code, "DICTIONARY_ITEM_UPDATED", requestId, authentication);
        return result;
    }

    private DictionaryTypeEntity scopedType(String code, Authentication authentication) {
        DataScopeService.ScopeFilter scope = dataScopes.filter(resolve(authentication), "dictionaries", authentication);
        return types.findScoped(code, scope).orElseThrow(() -> new SummerException(CommonErrorCode.NOT_FOUND));
    }

    private DictionaryTypeView view(DictionaryTypeEntity type) { return view(type, null); }
    private DictionaryTypeView view(DictionaryTypeEntity type, DataScopeService.ScopeFilter scope) {
        List<DictionaryItemEntity> values = scope == null ? items.findAllByTenantIdAndDictionaryCodeOrderBySortOrderAscValueAsc(type.tenantId(), type.code()) : items.findScoped(type.code(), scope);
        long version = type.publishedVersion() == null ? 0 : type.publishedVersion();
        return new DictionaryTypeView(type.code(), type.name(), type.published(), type.publicReadable(), type.enabled(), values.stream().map(DictionaryService::view).toList(), version, type.publishedVersion(), type.tenantId(), type.organizationId(), type.draftRevision());
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

    private DictionaryTypeEntity lockedType(String code, Object expected, Authentication auth) {
        scopedType(code, auth);
        DictionaryTypeEntity type = types.lock(resolve(auth), code).orElseThrow(() -> new SummerException(CommonErrorCode.NOT_FOUND));
        entityManager.refresh(type, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        if (!(expected instanceof Integer || expected instanceof Long) || ((Number) expected).longValue() < 0) throw invalid("expectedDraftRevision");
        if (type.draftRevision() != ((Number) expected).longValue()) throw new SummerException(DictionaryErrorCode.CONFLICT);
        return type;
    }

    private void advance(DictionaryTypeEntity type, Object expected) {
        if (types.advance(type.tenantId(), type.code(), ((Number) expected).longValue()) != 1) throw new SummerException(DictionaryErrorCode.CONFLICT);
        entityManager.refresh(type, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
    }

    @Transactional(readOnly = true)
    DictionaryDraftView draft(String code, Authentication auth) {
        DictionaryTypeEntity type = scopedType(code, auth);
        return draftView(type, auth);
    }

    private DictionaryDraftView draftView(DictionaryTypeEntity type, Authentication auth) {
        var values = items.findScoped(type.code(), dataScopes.filter(resolve(auth), "dictionaries", auth));
        return new DictionaryDraftView(type.code(), type.name(), type.publicReadable(), type.enabled(), type.draftRevision(),
                type.publishedVersion() == null ? 0 : type.publishedVersion(), values.stream().map(i -> new DictionaryDraftItem(i.id(), i.value(), i.label(), i.description(), i.enabled(), i.everPublished())).toList(), values.stream().map(DictionaryItemEntity::id).toList());
    }

    @Transactional
    DictionaryDraftView saveDraft(String code, DictionaryDraftRequest request, String requestId, Authentication auth) {
        DictionaryTypeEntity type = lockedType(code, request == null ? null : request.expectedDraftRevision(), auth);
        var all = items.findAllByTenantIdAndDictionaryCodeOrderBySortOrderAscValueAsc(type.tenantId(), code);
        var visible = items.findScoped(code, dataScopes.filter(resolve(auth), "dictionaries", auth));
        if (visible.size() != all.size()) throw new org.springframework.security.access.AccessDeniedException("Full dictionary maintenance required");
        dataScopes.assertWrite(resolve(auth), "dictionaries", auth, type.organizationId());
        if (request.name() == null || request.name().isBlank() || request.name().length() > 200 || request.publicReadable() == null || request.enabled() == null || request.items() == null || request.itemOrder() == null) throw invalid("draft");
        var existing = all.stream().collect(java.util.stream.Collectors.toMap(DictionaryItemEntity::id, i -> i));
        var ids = new java.util.HashSet<String>(); var values = new java.util.HashSet<String>();
        for (var i : request.items()) {
            if (i == null || i.id() == null || i.id().isBlank() || !ids.add(i.id()) || !values.add(validValue(i.value())) || i.label() == null || i.label().isBlank() || i.label().length() > 200 || (i.description() != null && i.description().length() > 2000)) throw invalid("items");
            if (!existing.containsKey(i.id()) && !i.id().startsWith("new:")) throw invalid("items");
        }
        if (request.itemOrder().stream().anyMatch(java.util.Objects::isNull) || request.itemOrder().size() != ids.size() || !new java.util.HashSet<>(request.itemOrder()).equals(ids)) throw invalid("itemOrder");
        boolean orderChanged = !request.itemOrder().equals(all.stream().map(DictionaryItemEntity::id).toList());
        // Permission follows the relative order of retained identities, not storage renumbering.
        boolean reorderedExisting = !request.itemOrder().stream().filter(existing::containsKey).toList().equals(
                all.stream().map(DictionaryItemEntity::id).filter(ids::contains).toList());
        boolean unchanged = type.name().equals(request.name()) && type.publicReadable() == request.publicReadable() && type.enabled() == request.enabled() && all.size() == request.items().size();
        if (unchanged) for (var i : request.items()) {
            var old = existing.get(i.id());
            if (old == null || !old.value().equals(i.value()) || !old.label().equals(i.label()) || !old.description().equals(i.description() == null ? "" : i.description()) || old.enabled() != i.enabled() || orderChanged) { unchanged = false; break; }
        }
        if (unchanged) return draftView(type, auth);
        if (!type.name().equals(request.name())) actions.require("dictionaries", "update", auth);
        if (type.publicReadable() != request.publicReadable()) actions.require("dictionaries", "publish", auth);
        if (type.enabled() != request.enabled()) actions.require("dictionaries", request.enabled() ? "publish" : "disable", auth);
        for (var old : all) if (!ids.contains(old.id())) {
            actions.require("dictionaries", "update", auth);
            if (old.everPublished()) throw new SummerException(DictionaryErrorCode.INVALID_STATE);
            items.delete(old);
        }
        // Flush deletions before reusing a never-published value; transaction rollback covers failures.
        items.flush();
        for (var i : request.items()) {
            var old = existing.get(i.id()); String value = (String) i.value(); int order = !orderChanged && old != null ? old.sortOrder() : request.itemOrder().indexOf(i.id());
            String description = i.description() == null ? "" : i.description();
            if (old == null) {
                actions.require("dictionaries", "create", auth);
                if (!i.enabled()) actions.require("dictionaries", "disable", auth);
                old = new DictionaryItemEntity(type.tenantId(), UUID.randomUUID().toString(), code, value, i.label(), order, i.enabled(), type.organizationId(), auth.getName(), auth.getName());
            } else {
                if (!old.value().equals(value) && old.everPublished()) throw new SummerException(DictionaryErrorCode.INVALID_STATE);
                if (!old.value().equals(value) || !old.label().equals(i.label()) || !old.description().equals(description) || reorderedExisting) actions.require("dictionaries", "update", auth);
                if (old.enabled() != i.enabled()) actions.require("dictionaries", i.enabled() ? "publish" : "disable", auth);
            }
            old.edit(value, description); old.change(i.label(), order, i.enabled()); items.save(old);
        }
        advance(type, request.expectedDraftRevision());
        type.change(request.name(), type.published(), request.publicReadable(), request.enabled());
        try { items.flush(); types.flush(); }
        catch (DataIntegrityViolationException exception) { throw new SummerException(DictionaryErrorCode.CONFLICT, exception); }
        audit.recordSuccess(code, "DICTIONARY_DRAFT_SAVED", requestId, auth);
        return draftView(type, auth);
    }

    private static SummerException invalid(String field) {
        return new SummerException(DictionaryErrorCode.INVALID,
                List.of(new SummerException.Violation(field, "is invalid")));
    }

    private static boolean bool(Boolean value) { return Boolean.TRUE.equals(value); }
    private static boolean bool(Boolean value, boolean fallback) { return value == null ? fallback : value; }
    private static boolean blank(String value) { return value == null || value.isBlank(); }
}
