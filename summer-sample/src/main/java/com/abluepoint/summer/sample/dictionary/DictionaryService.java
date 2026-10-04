package com.abluepoint.summer.sample.dictionary;

import com.abluepoint.summer.common.error.CommonErrorCode;
import com.abluepoint.summer.common.error.SummerException;
import com.abluepoint.summer.sample.cache.VersionedTwoLevelCache;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
class DictionaryService {
    private final DictionaryTypeRepository types;
    private final DictionaryItemRepository items;
    private final DictionaryAuditService audit;
    private final VersionedTwoLevelCache cache;

    DictionaryService(DictionaryTypeRepository types, DictionaryItemRepository items, DictionaryAuditService audit,
                      VersionedTwoLevelCache cache) {
        this.types = types;
        this.items = items;
        this.audit = audit;
        this.cache = cache;
    }

    @Transactional(readOnly = true)
    List<DictionaryTypeView> list() {
        return types.findAllByOrderByCodeAsc().stream().map(this::view).toList();
    }

    @Transactional
    DictionaryTypeView createType(DictionaryTypeRequest request, String requestId, Authentication authentication) {
        validateCode(request == null ? null : request.code());
        String code = request.code().trim();
        if (types.findByCode(code).isPresent()) throw new SummerException(DictionaryErrorCode.CONFLICT);
        if (blank(request.name())) throw invalid("name");
        DictionaryTypeEntity type = new DictionaryTypeEntity(UUID.randomUUID().toString(), code, request.name().trim(),
                bool(request.published()), bool(request.publicReadable()), bool(request.enabled(), true));
        try {
            DictionaryTypeView result = view(types.saveAndFlush(type));
            cache.invalidate("ent-acme", "dictionary");
            audit.recordSuccess(code, "DICTIONARY_CREATED", requestId, authentication);
            return result;
        } catch (DataIntegrityViolationException exception) {
            throw new SummerException(DictionaryErrorCode.CONFLICT, exception);
        }
    }

    @Transactional
    DictionaryTypeView updateType(String code, DictionaryTypeRequest request, String requestId, Authentication authentication) {
        DictionaryTypeEntity type = requireType(code);
        if (request == null || blank(request.name())) throw invalid("name");
        type.change(request.name().trim(), bool(request.published(), type.published()),
                bool(request.publicReadable(), type.publicReadable()), bool(request.enabled(), type.enabled()));
        DictionaryTypeView result = view(types.saveAndFlush(type));
        cache.invalidate("ent-acme", "dictionary");
        audit.recordSuccess(code, "DICTIONARY_UPDATED", requestId, authentication);
        return result;
    }

    @Transactional
    DictionaryItemView createItem(String code, DictionaryItemRequest request, String requestId, Authentication authentication) {
        DictionaryTypeEntity type = requireType(code);
        validateItem(request);
        if (items.findByDictionaryCodeAndValue(code, request.value().trim()).isPresent()) throw new SummerException(DictionaryErrorCode.CONFLICT);
        DictionaryItemEntity item = new DictionaryItemEntity(UUID.randomUUID().toString(), type.code(), request.value().trim(),
                request.label().trim(), request.sortOrder(), bool(request.enabled(), true));
        DictionaryItemView result;
        try {
            result = view(items.saveAndFlush(item));
        } catch (DataIntegrityViolationException exception) {
            throw new SummerException(DictionaryErrorCode.CONFLICT, exception);
        }
        cache.invalidate("ent-acme", "dictionary");
        audit.recordSuccess(code, "DICTIONARY_ITEM_CREATED", requestId, authentication);
        return result;
    }

    @Transactional
    DictionaryItemView updateItem(String code, String value, DictionaryItemRequest request, String requestId, Authentication authentication) {
        requireType(code);
        if (request == null || blank(request.label()) || request.sortOrder() == null || request.sortOrder() < 0) {
            throw invalid("item");
        }
        DictionaryItemEntity item = items.findByDictionaryCodeAndValue(code, value)
                .orElseThrow(() -> new SummerException(CommonErrorCode.NOT_FOUND));
        item.change(request.label().trim(), request.sortOrder(), bool(request.enabled(), item.enabled()));
        DictionaryItemView result = view(items.saveAndFlush(item));
        cache.invalidate("ent-acme", "dictionary");
        audit.recordSuccess(code, "DICTIONARY_ITEM_UPDATED", requestId, authentication);
        return result;
    }

    @Transactional(readOnly = true)
    PublicDictionaryView publicRead(String code) {
        return cache.get("ent-acme", "dictionary", code, PublicDictionaryView.class, () -> {
            DictionaryTypeEntity type = types.findByCode(code)
                    .filter(value -> value.enabled() && value.published() && value.publicReadable())
                    .orElseThrow(() -> new SummerException(CommonErrorCode.NOT_FOUND));
            List<DictionaryItemView> values = items.findAllByDictionaryCodeOrderBySortOrderAscValueAsc(type.code()).stream()
                    .filter(DictionaryItemEntity::enabled).map(DictionaryService::view).toList();
            return new PublicDictionaryView(type.code(), values);
        });
    }

    private DictionaryTypeEntity requireType(String code) {
        return types.findByCode(code).orElseThrow(() -> new SummerException(CommonErrorCode.NOT_FOUND));
    }

    private DictionaryTypeView view(DictionaryTypeEntity type) {
        return new DictionaryTypeView(type.code(), type.name(), type.published(), type.publicReadable(), type.enabled(),
                items.findAllByDictionaryCodeOrderBySortOrderAscValueAsc(type.code()).stream().map(DictionaryService::view).toList());
    }

    private static DictionaryItemView view(DictionaryItemEntity item) {
        return new DictionaryItemView(item.value(), item.label(), item.sortOrder(), item.enabled());
    }

    private static void validateCode(String code) {
        if (code == null || !code.matches("[a-z][a-z0-9-]{1,78}[a-z0-9]")) throw invalid("code");
    }

    private static void validateItem(DictionaryItemRequest request) {
        if (request == null || request.value() == null || !request.value().matches("[a-z0-9][a-z0-9._-]{0,78}[a-z0-9]")) throw invalid("value");
        if (blank(request.label()) || request.sortOrder() == null || request.sortOrder() < 0) throw invalid("item");
    }

    private static SummerException invalid(String field) {
        return new SummerException(DictionaryErrorCode.INVALID,
                List.of(new SummerException.Violation(field, "is invalid")));
    }

    private static boolean bool(Boolean value) { return Boolean.TRUE.equals(value); }
    private static boolean bool(Boolean value, boolean fallback) { return value == null ? fallback : value; }
    private static boolean blank(String value) { return value == null || value.isBlank(); }
}
