package com.abluepoint.summer.sample.authorization.application.view;

import java.util.List;

public record PolicyView(
        String id,
        String policyId,
        String enterpriseId,
        String name,
        String code,
        String description,
        String policyType,
        String lifecycleStatus,
        long version,
        Integer currentVersionNo,
        PolicyVersionView currentVersion,
        Integer draftVersion,
        List<PolicyVersionView> versions) {}
