package com.abluepoint.summer.sample.application;

import java.util.List;

record ApplicationCreateRequest(String name, String description, List<String> redirectUris) {}
record ApplicationUpdateRequest(String name, String description) {}
record OAuthConfigRequest(List<String> grantTypes, List<String> responseTypes, List<String> scopes,
                          String clientAuthMethod, Boolean pkceRequired, Boolean refreshTokenRotation,
                          List<String> redirectUris) {}
record CredentialView(String credentialId, int version, String status, String createdAt, String revokedAt, String clientSecret) {}
record ApplicationView(String enterpriseId, String applicationId, String name, String description, String status,
                       String clientId, List<String> redirectUris, OAuthConfigView oauthConfig,
                       List<CredentialView> credentials, long version, String clientSecret) {}
record OAuthConfigView(List<String> grantTypes, List<String> responseTypes, List<String> scopes,
                       String clientAuthMethod, boolean pkceRequired, boolean refreshTokenRotation,
                       List<String> redirectUris, long version) {}
