package com.abluepoint.summer.sample.dictionary.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DictionaryNotificationRepository extends JpaRepository<DictionaryNotificationEventEntity, Long> {
    public List<DictionaryNotificationEventEntity> findTop500ByTenantIdAndDictionaryCodeOrderByEventIdDesc(
            String tenantId, String dictionaryCode);
}
