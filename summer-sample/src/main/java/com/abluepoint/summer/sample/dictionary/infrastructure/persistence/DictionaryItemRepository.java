package com.abluepoint.summer.sample.dictionary.infrastructure.persistence;

import com.abluepoint.summer.sample.authorization.application.DataScopeService;
import org.springframework.data.jpa.repository.*;
import java.util.List;
import java.util.Optional;

public interface DictionaryItemRepository extends JpaRepository<DictionaryItemEntity, String>, JpaSpecificationExecutor<DictionaryItemEntity> {
    public List<DictionaryItemEntity> findAllByTenantIdAndDictionaryCodeOrderBySortOrderAscValueAsc(String tenantId, String dictionaryCode);
    public Optional<DictionaryItemEntity> findByTenantIdAndDictionaryCodeAndValue(String tenantId, String dictionaryCode, String value);
    public default List<DictionaryItemEntity> findScoped(String code, com.abluepoint.summer.sample.authorization.application.DataScopeService.ScopeFilter scope) {
        return findAll(scope.<DictionaryItemEntity>specification().and((root, query, builder) -> builder.equal(root.get("dictionaryCode"), code)), org.springframework.data.domain.Sort.by("sortOrder", "value"));
    }
    public default Optional<DictionaryItemEntity> findScoped(String code, String value, com.abluepoint.summer.sample.authorization.application.DataScopeService.ScopeFilter scope) {
        return findOne(scope.<DictionaryItemEntity>specification().and((root, query, builder) -> builder.and(builder.equal(root.get("dictionaryCode"), code), builder.equal(root.get("value"), value))));
    }
}
