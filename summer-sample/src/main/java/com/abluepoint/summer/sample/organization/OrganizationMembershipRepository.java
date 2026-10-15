package com.abluepoint.summer.sample.organization;

import org.springframework.data.jpa.repository.JpaRepository;

interface OrganizationMembershipRepository extends JpaRepository<OrganizationMembershipEntity, Long> {
    java.util.List<OrganizationMembershipEntity> findByEnterpriseIdAndMemberId(String enterpriseId, String memberId);
    void deleteByEnterpriseIdAndMemberId(String enterpriseId, String memberId);
}
