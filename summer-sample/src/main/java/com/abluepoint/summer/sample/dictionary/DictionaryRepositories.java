package com.abluepoint.summer.sample.dictionary;

import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

interface DictionaryTypeRepository extends JpaRepository<DictionaryTypeEntity, String>, JpaSpecificationExecutor<DictionaryTypeEntity> {
    interface Control {
        long getDraftRevision();
        long getPublishedVersion();
        long getEffectiveRevision();
    }
    @Query(value="select draft_revision as draftRevision, coalesce(published_version,0) as publishedVersion, effective_revision as effectiveRevision from summer_dictionary_type where tenant_id=:tenant and code=:code for update", nativeQuery=true)
    Control currentControl(@Param("tenant") String tenant, @Param("code") String code);

    @Modifying
    @Query("update DictionaryTypeEntity t set t.effectiveRevision=t.effectiveRevision+1 where t.tenantId=:tenant and t.code=:code and t.effectiveRevision=:effective and coalesce(t.publishedVersion,0)=:version and (:draft is null or t.draftRevision=:draft)")
    int claimEffective(@Param("tenant") String tenant, @Param("code") String code, @Param("effective") long effective, @Param("version") long version, @Param("draft") Long draft);
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
    default List<DictionaryTypeEntity> findScoped(com.abluepoint.summer.sample.authorization.BusinessDataScopes.Filter scope) {
        return findAll(scope.specification(), org.springframework.data.domain.Sort.by("code"));
    }
    default Optional<DictionaryTypeEntity> findScoped(String code, com.abluepoint.summer.sample.authorization.DataScopeService.ScopeFilter scope) {
        return findOne(scope.<DictionaryTypeEntity>specification().and((root, query, builder) -> builder.equal(root.get("code"), code)));
    }
    default Optional<DictionaryTypeEntity> findScoped(String code, com.abluepoint.summer.sample.authorization.BusinessDataScopes.Filter scope) {
        return findOne(scope.<DictionaryTypeEntity>specification().and((root, query, builder) -> builder.equal(root.get("code"), code)));
    }
}

interface DictionaryItemRepository extends JpaRepository<DictionaryItemEntity, String>, JpaSpecificationExecutor<DictionaryItemEntity> {
    @Modifying
    @Query("update DictionaryItemEntity i set i.everPublished=true where i.tenantId=:tenant and i.dictionaryCode=:code")
    int markPublished(@Param("tenant") String tenant, @Param("code") String code);
    @Query(value="select id from summer_dictionary_item where tenant_id=:tenant and dictionary_code=:code and ever_published=true for update", nativeQuery=true)
    List<String> publishedIds(@Param("tenant") String tenant, @Param("code") String code);
    @Query(value="select case when ever_published then 1 else 0 end from summer_dictionary_item where id=:id for update", nativeQuery=true)
    int publishedIdentity(@Param("id") String id);

    List<DictionaryItemEntity> findAllByTenantIdAndDictionaryCodeOrderBySortOrderAscValueAsc(String tenantId, String dictionaryCode);
    @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from DictionaryItemEntity i where i.tenantId=:tenant and i.dictionaryCode=:code order by i.sortOrder, i.value")
    List<DictionaryItemEntity> lockAll(@Param("tenant") String tenant, @Param("code") String code);
    Optional<DictionaryItemEntity> findByTenantIdAndDictionaryCodeAndValue(String tenantId, String dictionaryCode, String value);
    default List<DictionaryItemEntity> findScoped(String code, com.abluepoint.summer.sample.authorization.DataScopeService.ScopeFilter scope) {
        return findAll(scope.<DictionaryItemEntity>specification().and((root, query, builder) -> builder.equal(root.get("dictionaryCode"), code)), org.springframework.data.domain.Sort.by("sortOrder", "value"));
    }
    default List<DictionaryItemEntity> findScoped(String code, com.abluepoint.summer.sample.authorization.BusinessDataScopes.Filter scope) {
        return findAll(scope.<DictionaryItemEntity>specification().and((root, query, builder) -> builder.equal(root.get("dictionaryCode"), code)), org.springframework.data.domain.Sort.by("sortOrder", "value"));
    }
    default Optional<DictionaryItemEntity> findScoped(String code, String value, com.abluepoint.summer.sample.authorization.DataScopeService.ScopeFilter scope) {
        return findOne(scope.<DictionaryItemEntity>specification().and((root, query, builder) -> builder.and(builder.equal(root.get("dictionaryCode"), code), builder.equal(root.get("value"), value))));
    }
    default Optional<DictionaryItemEntity> findScoped(String code, String value, com.abluepoint.summer.sample.authorization.BusinessDataScopes.Filter scope) {
        return findOne(scope.<DictionaryItemEntity>specification().and((root, query, builder) -> builder.and(builder.equal(root.get("dictionaryCode"), code), builder.equal(root.get("value"), value))));
    }
}
