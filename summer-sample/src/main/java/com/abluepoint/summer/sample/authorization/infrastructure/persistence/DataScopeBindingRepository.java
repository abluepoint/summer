package com.abluepoint.summer.sample.authorization.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface DataScopeBindingRepository extends JpaRepository<DataScopeBindingEntity, String> {
    public List<DataScopeBindingEntity> findByEnterpriseIdAndResourceOrderBySubjectTypeAscSubjectIdAsc(String enterpriseId, String resource);
    public Optional<DataScopeBindingEntity> findByEnterpriseIdAndResourceAndSubjectTypeAndSubjectId(String enterpriseId, String resource, String subjectType, String subjectId);
}
