/*
 * Copyright (c) 2020 abluepoint All Rights Reserved.
 * File:JwtManager.java
 * Date:2020-12-29 17:33:29
 */

package com.abluepoint.summer.security.jwt;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;

import java.util.Date;
import java.util.UUID;

public class JwtManager {

    private String secret;

    public JwtManager(String secret) {
        this.secret = secret;
    }

    public String createJwtToken(String subject) {
		return createJwtToken(subject,UUID.randomUUID().toString());
    }

	public String createJwtToken(String subject,String jwtId) {

		//设置1天后过期
		Date date = new Date(System.currentTimeMillis() + 86400 * 1000);
		Algorithm algorithm = Algorithm.HMAC256(secret);
		// @format off
		String jwtToken = JWT.create()
			.withSubject(subject)
			.withExpiresAt(date)
			.withIssuedAt(new Date())
			.withJWTId(jwtId)
			.sign(algorithm);

		// @format on
		return jwtToken;

	}

    public void verify(String subject, String token) {
        Algorithm algorithm = Algorithm.HMAC256(secret);
        // @format off
        JWTVerifier verifier = JWT.require(algorithm)
			.withSubject(subject)
			.build();
        // @format on
        verifier.verify(token);
    }

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

}
