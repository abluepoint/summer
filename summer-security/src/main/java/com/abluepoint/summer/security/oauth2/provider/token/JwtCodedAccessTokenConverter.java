/*
 * Copyright (c) 2020 abluepoint All Rights Reserved.
 * File:JwtCodedAccessTokenConverter.java
 * Date:2020-12-29 17:33:29
 */

package com.abluepoint.summer.security.oauth2.provider.token;

import org.springframework.security.oauth2.common.OAuth2AccessToken;
import org.springframework.security.oauth2.provider.OAuth2Authentication;
import org.springframework.security.oauth2.provider.token.store.JwtAccessTokenConverter;

import java.util.Map;

public class JwtCodedAccessTokenConverter extends JwtAccessTokenConverter {
	public String encode(OAuth2AccessToken accessToken, OAuth2Authentication authentication) {
		return super.encode(accessToken,authentication);
	}

	public Map<String, Object> decode(String token) {
		return super.decode(token);
	}
}
