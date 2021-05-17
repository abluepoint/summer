/*
 * Copyright (c) 2020 abluepoint All Rights Reserved.
 * File:SummerException.java
 * Date:2020-11-12 17:42:12
 */

package com.abluepoint.summer.common.exception;


public class SummerException extends Exception implements Messageable {

    private static final long serialVersionUID = 5586244298123416199L;
    private static final String EMPTY_STRING = "";

    private MessageSupport messageSupport = new MessageSupport();

    public SummerException() {
        super();
        messageSupport.setMessageKey(EMPTY_STRING);
    }

    public SummerException(String message) {
        super(message);
        messageSupport.setMessageKey(message);
    }

    /**
     * Throwable类型的参数放在最后,不作为组装message的参数
     * @param message
     * @param args
     */
    public SummerException(String message, Object... args) {
        super(message);
        messageSupport.setMessageKey(message);
        messageSupport.setArgs(args);
    }

    public SummerException(String message, Throwable cause) {
        super(message, cause);
        messageSupport.setMessageKey(message);
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