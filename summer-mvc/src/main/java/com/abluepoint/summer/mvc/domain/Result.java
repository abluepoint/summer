package com.abluepoint.summer.mvc.domain;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.io.Serializable;

@JsonInclude(JsonInclude.Include.NON_EMPTY )
public class Result<T> implements Serializable {
    private int code;
    private String msg;
    private T data;

    public Result(int status, String message, T data) {
        init(status, message, data);
    }

    protected void init(int status, String message, T data){
        this.code = status;
        this.data = data;
        this.msg = message;
    }

    public int getCode() {
        return code;
    }

    public String getMsg() {
        return msg;
    }

    public T getData() {
        return data;
    }
}
