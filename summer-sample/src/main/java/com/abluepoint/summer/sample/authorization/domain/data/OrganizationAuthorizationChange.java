package com.abluepoint.summer.sample.authorization.domain.data;

/** Synchronous directory transaction seam: lock before enterprise facts; publish after mutation. */
public record OrganizationAuthorizationChange(
    String enterpriseId, String resourceId, String action, String requestId, boolean lockOnly) {}
