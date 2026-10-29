package com.abluepoint.summer.sample.authorization.domain;

import java.util.List;
import java.util.Map;

/** Administrative operations unavailable through protocol lookups, which hide disabled clients. */
public interface OAuthClientManagementStore {
    void registerPublicClient(String clientId, String clientName, List<String> redirectUris, List<String> scopes);
    List<Map<String, Object>> managementProjection();
    List<Map<String, Object>> managementConsents();
    Map<String, Object> updateManagement(String clientId, String status, String clientName,
                                         List<String> redirectUris, List<String> scopes, int expectedVersion);
}
