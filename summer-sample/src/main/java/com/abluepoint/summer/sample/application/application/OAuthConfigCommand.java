package com.abluepoint.summer.sample.application.application;

import java.util.List;

public record OAuthConfigCommand(List<String> grantTypes, List<String> responseTypes, List<String> scopes,
                          String clientAuthMethod, Boolean pkceRequired, Boolean refreshTokenRotation,
                          List<String> redirectUris) {
    // Preserve the pre-migration fingerprint label without rewriting field values.
    @Override public String toString() {
        return "OAuthConfigRequest[grantTypes=" + grantTypes + ", responseTypes=" + responseTypes + ", scopes=" + scopes + ", clientAuthMethod=" + clientAuthMethod + ", pkceRequired=" + pkceRequired + ", refreshTokenRotation=" + refreshTokenRotation + ", redirectUris=" + redirectUris + "]";
    }
}
