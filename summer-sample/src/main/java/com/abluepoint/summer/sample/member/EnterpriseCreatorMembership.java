package com.abluepoint.summer.sample.member;

import com.abluepoint.summer.sample.enterprise.DirectoryJournal;
import com.abluepoint.summer.sample.enterprise.EnterpriseCreated;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
class EnterpriseCreatorMembership {
    private final EnterpriseMemberRepository members;
    private final DirectoryJournal journal;
    EnterpriseCreatorMembership(EnterpriseMemberRepository members, DirectoryJournal journal) {
        this.members = members;
        this.journal = journal;
    }
    @EventListener
    @Transactional(propagation = Propagation.MANDATORY)
    public void create(EnterpriseCreated event) {
        // This event is emitted only when a new enterprise is inserted, never by role binding.
        if (members.findByEnterpriseIdAndSubject(event.enterpriseId(), event.creatorSubject()).isPresent())
            throw new IllegalStateException("Creator membership already exists");
        String id = "member-" + java.util.UUID.randomUUID();
        members.saveAndFlush(new EnterpriseMemberEntity(event.enterpriseId(), id, event.creatorSubject(), event.creatorSubject(), "", "ACTIVE"));
        journal.audit(event.enterpriseId(), "member.audit", id,
                new MemberService.AuditEvent(event.creatorSubject(), event.enterpriseId(), id, "MEMBER_CREATED", null, "ACTIVE", event.requestId()));
    }
}
