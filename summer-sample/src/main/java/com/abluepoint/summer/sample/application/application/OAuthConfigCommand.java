package com.abluepoint.summer.sample.application.application;

import java.util.List;

public record OAuthConfigCommand(List<String> grantTypes, List<String> responseTypes, List<String> scopes,
                          String clientAuthMethod, Boolean pkceRequired, Boolean refreshTokenRotation,
                          List<String> redirectUris) {}
