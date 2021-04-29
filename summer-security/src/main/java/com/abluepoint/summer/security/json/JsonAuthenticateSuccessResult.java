/*
 * Copyright (c) 2020 abluepoint All Rights Reserved.
 * File:JsonAuthenticateSuccessResult.java
 * Date:2020-03-24 10:06:24
 */

package com.abluepoint.summer.security.json;

import java.time.LocalDateTime;
import java.util.Date;

import com.abluepoint.summer.mvc.domain.HttpResult;
import org.springframework.http.HttpStatus;

public class JsonAuthenticateSuccessResult extends HttpResult {

    private String token;

    public JsonAuthenticateSuccessResult(LocalDateTime timestamp, String message, String token) {
        super(timestamp, HttpStatus.OK.value(), null, message, null);
        this.token = token;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

}
