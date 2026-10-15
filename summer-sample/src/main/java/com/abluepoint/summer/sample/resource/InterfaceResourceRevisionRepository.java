package com.abluepoint.summer.sample.resource;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

interface InterfaceResourceRevisionRepository extends JpaRepository<InterfaceResourceRevisionEntity, Long> {
    Optional<InterfaceResourceRevisionEntity> findByFingerprint(String fingerprint);
    List<InterfaceResourceRevisionEntity> findAllByOrderByRevisionDesc(Pageable page);
}
