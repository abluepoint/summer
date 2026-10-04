package com.abluepoint.summer.sample.organization;

import org.springframework.data.jpa.repository.JpaRepository;

interface OrganizationMembershipRepository extends JpaRepository<OrganizationMembershipEntity, Long> {
    void deleteByEnterpriseIdAndMemberId(String enterpriseId, String memberId);
}
