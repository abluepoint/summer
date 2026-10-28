package com.abluepoint.summer.sample.bff.infrastructure;

import com.abluepoint.summer.sample.bff.domain.OidcCodeExchanger;
import com.abluepoint.summer.sample.bff.domain.OidcIdentity;


public final class UnavailableOidcCodeExchanger implements OidcCodeExchanger {
    @Override
    public OidcIdentity exchange(String code, String codeVerifier) {
        throw new IllegalStateException("OIDC code exchanger is not configured");
    }
}
