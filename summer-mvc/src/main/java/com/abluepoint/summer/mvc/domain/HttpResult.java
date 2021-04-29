/*
 * Copyright (c) 2020 abluepoint All Rights Reserved.
 * File:HttpResult.java
 * Date:2020-12-31 19:57:31
 */

package com.abluepoint.summer.mvc.domain;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.springframework.http.HttpStatus;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * {
 *     "timestamp": "2020-01-11T08:15:12.219+0000",
 *     "status": 404,
 *     "error": "Not Found",
 *     "message": "No message available",
 *     "path": "/user/info"
 * }
 */
@JsonInclude(value = JsonInclude.Include.NON_NULL)
public class HttpResult implements Serializable {
    private LocalDateTime timestamp;
    private int status;
    private String error;
    private String message;
    private String path;

    public HttpResult(LocalDateTime timestamp, HttpStatus httpStatus, String message) {
        this(timestamp, httpStatus.value(), httpStatus.getReasonPhrase(), message);
    }

    public HttpResult(LocalDateTime timestamp, int status, String error, String message) {
        this(timestamp, status, error, message, null);
    }

    public HttpResult(LocalDateTime timestamp, int status, String error, String message, String path) {
        this.timestamp = timestamp;
        this.status = status;
        this.error = error;
        this.message = message;
        this.path = path;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public int getStatus() {
        return status;
    }

    public String getError() {
        return error;
    }

    public String getMessage() {
        return message;
    }

    public String getPath() {
        return path;
    }
}
