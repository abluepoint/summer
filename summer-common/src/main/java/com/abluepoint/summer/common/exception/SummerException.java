/*
 * Copyright (c) 2020 abluepoint All Rights Reserved.
 * File:SummerException.java
 * Date:2020-11-12 17:42:12
 */

package com.abluepoint.summer.common.exception;

public class SummerException extends Exception implements Messageable {

    private static final long serialVersionUID = 5586244298123416199L;

    private MessageSupport messageSupport = new MessageSupport();

    public SummerException() {
        super();
        messageSupport.setMessageKey("");
    }

    public SummerException(String message) {
        super(message);
        messageSupport.setMessageKey(message);
    }

    public SummerException(String message, Object... args) {
        super(message);
        messageSupport.setMessageKey(message);
        messageSupport.setArgs(args);
    }

    public SummerException(String message, Throwable cause) {
        super(message, cause);
        messageSupport.setMessageKey(message);
    }

    public SummerException(Throwable cause,String message, Object... args) {
        super(message, cause);
        messageSupport.setMessageKey(message);
        messageSupport.setArgs(args);
    }

    @Override
    public String getMessageKey() {
        return messageSupport.getMessageKey();
    }

    @Override
    public Object[] getArgs() {
        return messageSupport.getArgs();
    }

    @Override
    public String getMessage() {
        StringBuilder sb = new StringBuilder();
        MessageSupport.exportMessage(messageSupport, sb);
        return sb.toString();
    }

}