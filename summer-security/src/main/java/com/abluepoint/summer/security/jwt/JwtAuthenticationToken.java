/*
 * Copyright (c) 2020 abluepoint All Rights Reserved.
 * File:JwtAuthenticationToken.java
 * Date:2020-12-29 17:33:29
 */

package com.abluepoint.summer.security.jwt;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.Collections;

public class JwtAuthenticationToken extends AbstractAuthenticationToken {

    private static final long serialVersionUID = 3981518947978158945L;

    private UserDetails principal;
    private String credentials;


	public JwtAuthenticationToken(String credentials,Collection<? extends GrantedAuthority> authorities) {
		super(authorities);
		this.credentials = credentials;
	}

    public JwtAuthenticationToken(String credentials) {
        this(credentials,Collections.emptyList());
    }

    public JwtAuthenticationToken(UserDetails principal, String credentials,Collection<? extends GrantedAuthority> authorities) {
        super(authorities);
        this.principal = principal;
        this.credentials = credentials;
    }

	protected void setPrincipal(UserDetails principal) {
		this.principal = principal;
	}

	@Override
    public void setDetails(Object details) {
        super.setDetails(details);
        this.setAuthenticated(true);
    }

    @Override
    public Object getCredentials() {
        return credentials;
    }

    @Override
    public Object getPrincipal() {
        return principal;
    }


}
