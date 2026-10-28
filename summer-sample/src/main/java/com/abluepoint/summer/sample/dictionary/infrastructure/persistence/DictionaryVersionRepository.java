package com.abluepoint.summer.sample.dictionary.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface DictionaryVersionRepository extends JpaRepository<DictionaryVersionEntity, String> {
    public Optional<DictionaryVersionEntity> findTopByTenantIdAndDictionaryCodeOrderByVersionDesc(String tenantId, String dictionaryCode);
    public Optional<DictionaryVersionEntity> findByTenantIdAndDictionaryCodeAndVersion(String tenantId, String dictionaryCode, long version);
    public List<DictionaryVersionEntity> findAllByTenantIdAndDictionaryCodeOrderByVersionDesc(String tenantId, String dictionaryCode);
}
