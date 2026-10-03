package com.abluepoint.summer.sample.organization;

record OrganizationMember(
        String enterpriseId,
        String organizationId,
        String memberId,
        String displayName,
        String email,
        String status,
        boolean primary
) {}
