package com.abluepoint.summer.sample.dictionary.infrastructure.persistence;

import com.abluepoint.summer.sample.authorization.application.DataScopeService;
import org.springframework.data.jpa.repository.*;
import java.util.List;
import java.util.Optional;

public interface DictionaryTypeRepository extends JpaRepository<DictionaryTypeEntity, String>, JpaSpecificationExecutor<DictionaryTypeEntity> {
    public Optional<DictionaryTypeEntity> findByTenantIdAndCode(String tenantId, String code);
    public List<DictionaryTypeEntity> findAllByOrderByCodeAsc();
    public default List<DictionaryTypeEntity> findScoped(com.abluepoint.summer.sample.authorization.application.DataScopeService.ScopeFilter scope) {
        return findAll(scope.specification(), org.springframework.data.domain.Sort.by("code"));
    }
    public default Optional<DictionaryTypeEntity> findScoped(String code, com.abluepoint.summer.sample.authorization.application.DataScopeService.ScopeFilter scope) {
        return findOne(scope.<DictionaryTypeEntity>specification().and((root, query, builder) -> builder.equal(root.get("code"), code)));
    }
}
