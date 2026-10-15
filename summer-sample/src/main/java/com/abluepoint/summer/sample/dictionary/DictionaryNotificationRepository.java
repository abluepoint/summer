package com.abluepoint.summer.sample.dictionary;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

interface DictionaryNotificationRepository extends JpaRepository<DictionaryNotificationEventEntity, Long> {
    List<DictionaryNotificationEventEntity> findTop500ByTenantIdAndDictionaryCodeOrderByEventIdDesc(
            String tenantId, String dictionaryCode);
}
