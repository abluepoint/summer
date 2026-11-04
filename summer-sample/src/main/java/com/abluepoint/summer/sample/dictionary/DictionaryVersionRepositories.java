package com.abluepoint.summer.sample.dictionary;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

interface DictionaryVersionRepository extends JpaRepository<DictionaryVersionEntity, String> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    Optional<DictionaryVersionEntity> findTopByTenantIdAndDictionaryCodeOrderByVersionDesc(String tenantId, String dictionaryCode);
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_READ)
    @org.springframework.data.jpa.repository.Query("select v from DictionaryVersionEntity v where v.tenantId=:tenant and v.dictionaryCode=:code and v.version=:version")
    Optional<DictionaryVersionEntity> current(@org.springframework.data.repository.query.Param("tenant") String tenant,
            @org.springframework.data.repository.query.Param("code") String code, @org.springframework.data.repository.query.Param("version") long version);
    Optional<DictionaryVersionEntity> findByTenantIdAndDictionaryCodeAndVersion(String tenantId, String dictionaryCode, long version);
    List<DictionaryVersionEntity> findAllByTenantIdAndDictionaryCodeOrderByVersionDesc(String tenantId, String dictionaryCode);
}

interface DictionaryOutboxRepository extends JpaRepository<DictionaryOutboxEntity, String> {
    List<DictionaryOutboxEntity> findAllByTenantIdAndDictionaryCodeOrderByCreatedAtDesc(String tenantId, String dictionaryCode);
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    Optional<DictionaryOutboxEntity> findByTenantIdAndDictionaryCodeAndIdempotencyKey(String tenantId, String dictionaryCode, String idempotencyKey);
}
