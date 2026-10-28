package com.abluepoint.summer.sample.application.application;

import java.util.List;
import java.time.Instant;

public record ApplicationView(String enterpriseId, String applicationId, String name, String description, String status,
                       String clientId, List<String> redirectUris, OAuthConfigView oauthConfig,
                       List<CredentialView> credentials, long version, String clientSecret) {}
