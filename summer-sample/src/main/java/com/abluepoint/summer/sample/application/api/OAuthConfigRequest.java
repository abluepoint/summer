package com.abluepoint.summer.sample.application.api;

import java.util.List;
import com.abluepoint.summer.sample.application.application.*;

record OAuthConfigRequest(List<String> grantTypes, List<String> responseTypes, List<String> scopes,
                          String clientAuthMethod, Boolean pkceRequired, Boolean refreshTokenRotation,
                          List<String> redirectUris) {
    OAuthConfigCommand command() { return new OAuthConfigCommand(grantTypes, responseTypes, scopes, clientAuthMethod, pkceRequired, refreshTokenRotation, redirectUris); }
}
