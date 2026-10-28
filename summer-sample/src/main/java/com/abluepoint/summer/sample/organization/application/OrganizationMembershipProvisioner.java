package com.abluepoint.summer.sample.organization.application;

import com.abluepoint.summer.sample.organization.infrastructure.OrganizationMembershipEntity;
import com.abluepoint.summer.sample.organization.infrastructure.OrganizationMembershipRepository;
import com.abluepoint.summer.sample.organization.infrastructure.OrganizationUnitRepository;

import com.abluepoint.summer.sample.enterprise.application.EnterpriseRegistry;
import com.abluepoint.summer.sample.member.infrastructure.MemberDirectory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Internal bridge for an accepted invitation; callers establish the authenticated member first. */
@Service
@org.springframework.modulith.NamedInterface("membership")
public class OrganizationMembershipProvisioner {
    private final OrganizationUnitRepository units;
    private final OrganizationMembershipRepository assignments;
    private final MemberDirectory members;
    private final EnterpriseRegistry enterprises;
    public OrganizationMembershipProvisioner(OrganizationUnitRepository units, OrganizationMembershipRepository assignments, MemberDirectory members, EnterpriseRegistry enterprises) {
        this.units = units; this.assignments = assignments; this.members = members; this.enterprises = enterprises;
    }
    @Transactional
    public void assign(String enterpriseId, String memberId, String organizationId) {
        enterprises.lock(enterpriseId);
        if (members.find(enterpriseId, memberId).filter(member -> com.abluepoint.summer.sample.organization.domain.OrganizationRules.activeMember(member.status())).isEmpty())
            throw new IllegalArgumentException("An active enterprise member is required");
        if (units.findByUnitId(organizationId).filter(unit -> com.abluepoint.summer.sample.organization.domain.OrganizationRules.activeOrganization(enterpriseId, unit.enterpriseId(), unit.status())).isEmpty())
            throw new IllegalArgumentException("An active organization in this enterprise is required");
        var current = assignments.findByEnterpriseIdAndMemberId(enterpriseId, memberId);
        if (current.stream().noneMatch(value -> organizationId.equals(value.organizationId())))
            assignments.saveAndFlush(new OrganizationMembershipEntity(enterpriseId, memberId, organizationId, current.isEmpty()));
    }
}
