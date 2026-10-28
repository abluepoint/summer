package com.abluepoint.summer.sample.member.infrastructure;

import com.abluepoint.summer.data.jpa.persistence.api.BaseLongEntity;
import jakarta.persistence.*;

@Entity
@Table(name = "summer_enterprise_member", uniqueConstraints = {
        @UniqueConstraint(name = "uk_summer_member_tenant_id", columnNames = {"enterprise_id", "member_id"}),
        @UniqueConstraint(name = "uk_summer_member_tenant_subject", columnNames = {"enterprise_id", "subject"})})
public class EnterpriseMemberEntity extends BaseLongEntity {
    @Column(nullable = false, length = 64) String enterpriseId;
    @Column(nullable = false, length = 64) String memberId;
    @Column(nullable = false, length = 128) String subject;
    @Column(nullable = false, length = 200) String displayName;
    @Column(nullable = false, length = 254) String email;
    @Column(nullable = false, length = 32) String status;
    @Version long version;
    protected EnterpriseMemberEntity() {}
    EnterpriseMemberEntity(String enterpriseId, String memberId, String subject, String displayName, String email, String status) {
        this.enterpriseId = enterpriseId; this.memberId = memberId; this.subject = subject;
        this.displayName = displayName; this.email = email; this.status = status;
    }
}
