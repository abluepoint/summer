package com.abluepoint.summer.sample.dictionary;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

interface DictionaryTypeRepository extends JpaRepository<DictionaryTypeEntity, String> {
    Optional<DictionaryTypeEntity> findByCode(String code);
    List<DictionaryTypeEntity> findAllByOrderByCodeAsc();
}

interface DictionaryItemRepository extends JpaRepository<DictionaryItemEntity, String> {
    List<DictionaryItemEntity> findAllByDictionaryCodeOrderBySortOrderAscValueAsc(String dictionaryCode);
    Optional<DictionaryItemEntity> findByDictionaryCodeAndValue(String dictionaryCode, String value);
}
