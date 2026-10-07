package com.abluepoint.summer.sample.dictionary;

import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

interface DictionaryTypeRepository extends JpaRepository<DictionaryTypeEntity, String>, JpaSpecificationExecutor<DictionaryTypeEntity> {
    Optional<DictionaryTypeEntity> findByCode(String code);
    List<DictionaryTypeEntity> findAllByOrderByCodeAsc();
    default List<DictionaryTypeEntity> findScoped(com.abluepoint.summer.sample.authorization.DataScopeService.ScopeFilter scope) {
        return findAll(scope.specification(), org.springframework.data.domain.Sort.by("code"));
    }
    default Optional<DictionaryTypeEntity> findScoped(String code, com.abluepoint.summer.sample.authorization.DataScopeService.ScopeFilter scope) {
        return findOne(scope.<DictionaryTypeEntity>specification().and((root, query, builder) -> builder.equal(root.get("code"), code)));
    }
}

interface DictionaryItemRepository extends JpaRepository<DictionaryItemEntity, String>, JpaSpecificationExecutor<DictionaryItemEntity> {
    List<DictionaryItemEntity> findAllByDictionaryCodeOrderBySortOrderAscValueAsc(String dictionaryCode);
    Optional<DictionaryItemEntity> findByDictionaryCodeAndValue(String dictionaryCode, String value);
    default List<DictionaryItemEntity> findScoped(String code, com.abluepoint.summer.sample.authorization.DataScopeService.ScopeFilter scope) {
        return findAll(scope.<DictionaryItemEntity>specification().and((root, query, builder) -> builder.equal(root.get("dictionaryCode"), code)), org.springframework.data.domain.Sort.by("sortOrder", "value"));
    }
    default Optional<DictionaryItemEntity> findScoped(String code, String value, com.abluepoint.summer.sample.authorization.DataScopeService.ScopeFilter scope) {
        return findOne(scope.<DictionaryItemEntity>specification().and((root, query, builder) -> builder.and(builder.equal(root.get("dictionaryCode"), code), builder.equal(root.get("value"), value))));
    }
}
