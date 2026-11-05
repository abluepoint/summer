package com.abluepoint.summer.sample.organization;

import com.abluepoint.summer.sample.authorization.domain.data.OrganizationAuthorizationChange;

import com.abluepoint.summer.sample.enterprise.EnterpriseRegistry;
import com.abluepoint.summer.sample.member.MemberDirectory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Internal bridge for an accepted invitation; callers establish the authenticated member first. */
@Service
public class OrganizationMembershipProvisioner {
    @org.springframework.beans.factory.annotation.Autowired
    private org.springframework.context.ApplicationEventPublisher publisher;
    private final OrganizationUnitRepository units;
    private final OrganizationMembershipRepository assignments;
    private final MemberDirectory members;
    private final EnterpriseRegistry enterprises;
    public OrganizationMembershipProvisioner(OrganizationUnitRepository units, OrganizationMembershipRepository assignments, MemberDirectory members, EnterpriseRegistry enterprises) {
        this.units = units; this.assignments = assignments; this.members = members; this.enterprises = enterprises;
    }
    @Transactional
    public void assign(String enterpriseId, String memberId, String organizationId) {
        publisher.publishEvent(new OrganizationAuthorizationChange(enterpriseId, memberId, "", null, true));
        enterprises.lock(enterpriseId);
        if (members.find(enterpriseId, memberId).filter(member -> "ACTIVE".equals(member.status())).isEmpty())
            throw new IllegalArgumentException("An active enterprise member is required");
        if (units.findByUnitId(organizationId).filter(unit -> enterpriseId.equals(unit.enterpriseId()) && "ACTIVE".equals(unit.status())).isEmpty())
            throw new IllegalArgumentException("An active organization in this enterprise is required");
        var current = assignments.findByEnterpriseIdAndMemberId(enterpriseId, memberId);
        if (current.stream().noneMatch(value -> organizationId.equals(value.organizationId()))) {
            assignments.saveAndFlush(new OrganizationMembershipEntity(enterpriseId, memberId, organizationId, current.isEmpty()));
            publisher.publishEvent(new OrganizationAuthorizationChange(enterpriseId,memberId,"MEMBER_ORGANIZATION_ASSIGNED",null,false));
        }
    }
}
