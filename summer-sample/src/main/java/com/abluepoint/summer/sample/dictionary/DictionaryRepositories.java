package com.abluepoint.summer.sample.dictionary;

import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

interface DictionaryTypeRepository extends JpaRepository<DictionaryTypeEntity, String>, JpaSpecificationExecutor<DictionaryTypeEntity> {
    @Modifying
    @Query("update DictionaryTypeEntity t set t.draftRevision = t.draftRevision + 1 where t.tenantId = :tenant and t.code = :code and t.draftRevision = :expected")
    int advance(@Param("tenant") String tenant, @Param("code") String code, @Param("expected") long expected);
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from DictionaryTypeEntity t where t.tenantId = :tenant and t.code = :code")
    Optional<DictionaryTypeEntity> lock(@Param("tenant") String tenant, @Param("code") String code);
    Optional<DictionaryTypeEntity> findByTenantIdAndCode(String tenantId, String code);
    List<DictionaryTypeEntity> findAllByOrderByCodeAsc();
    default List<DictionaryTypeEntity> findScoped(com.abluepoint.summer.sample.authorization.DataScopeService.ScopeFilter scope) {
        return findAll(scope.specification(), org.springframework.data.domain.Sort.by("code"));
    }
    default Optional<DictionaryTypeEntity> findScoped(String code, com.abluepoint.summer.sample.authorization.DataScopeService.ScopeFilter scope) {
        return findOne(scope.<DictionaryTypeEntity>specification().and((root, query, builder) -> builder.equal(root.get("code"), code)));
    }
}

interface DictionaryItemRepository extends JpaRepository<DictionaryItemEntity, String>, JpaSpecificationExecutor<DictionaryItemEntity> {
    List<DictionaryItemEntity> findAllByTenantIdAndDictionaryCodeOrderBySortOrderAscValueAsc(String tenantId, String dictionaryCode);
    Optional<DictionaryItemEntity> findByTenantIdAndDictionaryCodeAndValue(String tenantId, String dictionaryCode, String value);
    default List<DictionaryItemEntity> findScoped(String code, com.abluepoint.summer.sample.authorization.DataScopeService.ScopeFilter scope) {
        return findAll(scope.<DictionaryItemEntity>specification().and((root, query, builder) -> builder.equal(root.get("dictionaryCode"), code)), org.springframework.data.domain.Sort.by("sortOrder", "value"));
    }
    default Optional<DictionaryItemEntity> findScoped(String code, String value, com.abluepoint.summer.sample.authorization.DataScopeService.ScopeFilter scope) {
        return findOne(scope.<DictionaryItemEntity>specification().and((root, query, builder) -> builder.and(builder.equal(root.get("dictionaryCode"), code), builder.equal(root.get("value"), value))));
    }
}
