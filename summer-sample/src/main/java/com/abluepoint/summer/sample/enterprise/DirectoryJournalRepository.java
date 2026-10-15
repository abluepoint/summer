package com.abluepoint.summer.sample.enterprise;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

interface DirectoryJournalRepository extends JpaRepository<DirectoryJournalEntity, Long> {
    Optional<DirectoryJournalEntity> findByEnterpriseIdAndNamespaceAndOperationKey(String enterpriseId, String namespace, String operationKey);
    List<DirectoryJournalEntity> findByEnterpriseIdAndNamespaceAndResourceIdOrderById(String enterpriseId, String namespace, String resourceId);
}
