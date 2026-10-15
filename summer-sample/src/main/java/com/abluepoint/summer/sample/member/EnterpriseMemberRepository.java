package com.abluepoint.summer.sample.member;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

interface EnterpriseMemberRepository extends JpaRepository<EnterpriseMemberEntity, Long> {
    List<EnterpriseMemberEntity> findByEnterpriseIdOrderByMemberId(String enterpriseId);
    Optional<EnterpriseMemberEntity> findByEnterpriseIdAndMemberId(String enterpriseId, String memberId);
    Optional<EnterpriseMemberEntity> findByEnterpriseIdAndSubject(String enterpriseId, String subject);
}
