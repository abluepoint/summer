package com.abluepoint.summer.sample.bff;

interface OidcCodeExchanger {

    OidcIdentity exchange(String code, String codeVerifier);
}
