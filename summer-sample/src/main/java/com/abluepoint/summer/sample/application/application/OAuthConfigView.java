package com.abluepoint.summer.sample.application.application;

import java.util.List;
import java.time.Instant;

public record OAuthConfigView(List<String> grantTypes, List<String> responseTypes, List<String> scopes,
                       String clientAuthMethod, boolean pkceRequired, boolean refreshTokenRotation,
                       List<String> redirectUris, long version) {}
