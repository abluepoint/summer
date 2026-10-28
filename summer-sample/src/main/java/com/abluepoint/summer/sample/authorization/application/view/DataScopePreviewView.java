package com.abluepoint.summer.sample.authorization.application.view;

import java.util.List;
import java.util.Map;

public record DataScopePreviewView(
        String enterpriseId,
        String resource,
        String subject,
        boolean allowed,
        String decision,
        String reasonCode,
        String explanation,
        List<String> matchedConditions,
        List<String> deniedConditions,
        long policyVersion,
        String checksum,
        Map<String, Object> effectiveScope) {}
