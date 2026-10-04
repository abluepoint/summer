package com.abluepoint.summer.sample.dictionary;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

interface DictionaryVersionRepository extends JpaRepository<DictionaryVersionEntity, String> {
    Optional<DictionaryVersionEntity> findTopByTenantIdAndDictionaryCodeOrderByVersionDesc(String tenantId, String dictionaryCode);
    Optional<DictionaryVersionEntity> findByTenantIdAndDictionaryCodeAndVersion(String tenantId, String dictionaryCode, long version);
    List<DictionaryVersionEntity> findAllByTenantIdAndDictionaryCodeOrderByVersionDesc(String tenantId, String dictionaryCode);
}

interface DictionaryOutboxRepository extends JpaRepository<DictionaryOutboxEntity, String> {
    List<DictionaryOutboxEntity> findAllByTenantIdAndDictionaryCodeOrderByCreatedAtDesc(String tenantId, String dictionaryCode);
    Optional<DictionaryOutboxEntity> findByTenantIdAndDictionaryCodeAndIdempotencyKey(String tenantId, String dictionaryCode, String idempotencyKey);
}
