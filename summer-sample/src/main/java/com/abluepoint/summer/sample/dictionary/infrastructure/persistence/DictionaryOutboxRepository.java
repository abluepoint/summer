package com.abluepoint.summer.sample.dictionary.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface DictionaryOutboxRepository extends JpaRepository<DictionaryOutboxEntity, String> {
    public List<DictionaryOutboxEntity> findAllByTenantIdAndDictionaryCodeOrderByCreatedAtDesc(String tenantId, String dictionaryCode);
    public Optional<DictionaryOutboxEntity> findByTenantIdAndDictionaryCodeAndIdempotencyKey(String tenantId, String dictionaryCode, String idempotencyKey);
}
