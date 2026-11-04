package com.abluepoint.summer.sample.dictionary;

import com.abluepoint.summer.common.error.CommonErrorCode;
import com.abluepoint.summer.common.error.SummerException;
import com.abluepoint.summer.sample.authorization.DataScopeService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.util.Set;
import java.util.stream.Collectors;

/** One base snapshot boundary for management, public projection and override validation. */
@Service
class EffectiveDictionaryReader {
    private final DictionaryTypeRepository types;
    private final DictionaryVersionRepository versions;
    private final DictionaryItemRepository items;
    private final DataScopeService scopes;
    private final ObjectMapper mapper;

    EffectiveDictionaryReader(DictionaryTypeRepository types, DictionaryVersionRepository versions,
                              DictionaryItemRepository items, DataScopeService scopes, ObjectMapper mapper) {
        this.types = types;
        this.versions = versions;
        this.items = items;
        this.scopes = scopes;
        this.mapper = mapper;
    }

    Effective read(String tenant, String code, Authentication auth, boolean publicRead) {
        var scope = scopes.filter(tenant, "dictionaries", auth);
        var type = types.findScoped(code, scope).orElseThrow(EffectiveDictionaryReader::missing);
        if (!type.published() || type.publishedVersion() == null) {
            if (publicRead) throw missing();
            return new Effective(code, null, type.publishedVersion() == null ? 0 : type.publishedVersion(), Set.of(), scope.fingerprint(), type.effectiveRevision(), type.draftRevision());
        }
        var version = versions.findByTenantIdAndDictionaryCodeAndVersion(tenant, code, type.publishedVersion())
                .orElseThrow(EffectiveDictionaryReader::missing);
        var snapshot = mapper.readValue(version.snapshotJson(), DictionarySnapshot.class);
        if (publicRead && (!snapshot.publicReadable() || !snapshot.enabled())) throw missing();
        Set<String> visible = items.findScoped(code, scope).stream().map(DictionaryItemEntity::value).collect(Collectors.toSet());
        return new Effective(code, snapshot, version.version(), visible, scope.fingerprint(), type.effectiveRevision(), type.draftRevision());
    }

    record Effective(String code, DictionarySnapshot snapshot, long publishedVersion,
                     Set<String> visibleValues, String scopeFingerprint, long effectiveRevision, long draftRevision) {
        Set<String> selectableValues() {
            return snapshot == null ? Set.of() : snapshot.items().stream().filter(DictionaryItemView::enabled)
                    .map(DictionaryItemView::value).filter(visibleValues::contains).collect(Collectors.toSet());
        }
    }

    private static SummerException missing() { return new SummerException(CommonErrorCode.NOT_FOUND); }
}
