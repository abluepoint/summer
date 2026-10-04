package com.abluepoint.summer.sample.dictionary;

import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

interface DictionaryTypeRepository extends JpaRepository<DictionaryTypeEntity, String> {
    Optional<DictionaryTypeEntity> findByCode(String code);
    List<DictionaryTypeEntity> findAllByOrderByCodeAsc();
    @Query("select t from DictionaryTypeEntity t where t.tenantId = :tenant and ((:scopeAnd = true and (:selfScope = false or (:subject is not null and t.createdBy = :subject)) and (:creatorScope = false or (:subject is not null and t.createdBy = :subject)) and (:ownerScope = false or (:subject is not null and t.ownerId = :subject)) and (:orgScope = false or (:organization is not null and t.organizationId = :organization))) or (:scopeAnd = false and (:tenantScope = true or (:subject is not null and ((:selfScope = true and t.createdBy = :subject) or (:creatorScope = true and t.createdBy = :subject) or (:ownerScope = true and t.ownerId = :subject) or (:orgScope = true and t.organizationId = :organization)))))) order by t.code asc")
    List<DictionaryTypeEntity> findScoped(@Param("tenant") String tenant, @Param("tenantScope") boolean tenantScope, @Param("scopeAnd") boolean scopeAnd, @Param("subject") String subject, @Param("selfScope") boolean selfScope, @Param("creatorScope") boolean creatorScope, @Param("ownerScope") boolean ownerScope, @Param("orgScope") boolean orgScope, @Param("organization") String organization);
}

interface DictionaryItemRepository extends JpaRepository<DictionaryItemEntity, String> {
    List<DictionaryItemEntity> findAllByDictionaryCodeOrderBySortOrderAscValueAsc(String dictionaryCode);
    Optional<DictionaryItemEntity> findByDictionaryCodeAndValue(String dictionaryCode, String value);
    @Query("select i from DictionaryItemEntity i where i.dictionaryCode = :code and i.tenantId = :tenant and ((:scopeAnd = true and (:selfScope = false or (:subject is not null and i.createdBy = :subject)) and (:creatorScope = false or (:subject is not null and i.createdBy = :subject)) and (:ownerScope = false or (:subject is not null and i.ownerId = :subject)) and (:orgScope = false or (:organization is not null and i.organizationId = :organization))) or (:scopeAnd = false and (:tenantScope = true or (:subject is not null and ((:selfScope = true and i.createdBy = :subject) or (:creatorScope = true and i.createdBy = :subject) or (:ownerScope = true and i.ownerId = :subject) or (:orgScope = true and i.organizationId = :organization)))))) order by i.sortOrder asc, i.value asc")
    List<DictionaryItemEntity> findScoped(@Param("code") String code, @Param("tenant") String tenant, @Param("tenantScope") boolean tenantScope, @Param("scopeAnd") boolean scopeAnd, @Param("subject") String subject, @Param("selfScope") boolean selfScope, @Param("creatorScope") boolean creatorScope, @Param("ownerScope") boolean ownerScope, @Param("orgScope") boolean orgScope, @Param("organization") String organization);
}
