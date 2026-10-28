package com.abluepoint.summer.sample.bff.domain;

public interface OidcCodeExchanger {

    OidcIdentity exchange(String code, String codeVerifier);
}
