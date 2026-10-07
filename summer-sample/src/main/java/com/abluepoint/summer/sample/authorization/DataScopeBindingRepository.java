package com.abluepoint.summer.sample.authorization;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

interface DataScopeBindingRepository extends JpaRepository<DataScopeBindingEntity, String> {
    List<DataScopeBindingEntity> findByEnterpriseIdAndResourceOrderBySubjectTypeAscSubjectIdAsc(String enterpriseId, String resource);
    Optional<DataScopeBindingEntity> findByEnterpriseIdAndResourceAndSubjectTypeAndSubjectId(String enterpriseId, String resource, String subjectType, String subjectId);
}
