package com.abluepoint.summer.sample.authorization.api.request;

import java.util.List;

public record DataScopePreviewRequest(
        String subject,
        String organizationId,
        String creatorId,
        String ownerId,
        List<String> roleAllows,
        List<String> userDenies,
        List<String> organizationIds) {
    public DataScopePreviewRequest(String subject, String organizationId, String creatorId, String ownerId, List<String> roleAllows, List<String> userDenies) {
        this(subject, organizationId, creatorId, ownerId, roleAllows, userDenies, List.of());
    }
}
