package com.abluepoint.summer.sample.organization;

record OrganizationUnitRequest(
        String parentId,
        String unitType,
        String name,
        String code,
        String leaderMemberId,
        String description,
        Integer sortOrder,
        String status
) {}
