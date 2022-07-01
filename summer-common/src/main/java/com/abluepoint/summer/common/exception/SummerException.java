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

    public SummerException(CodeMessage codeMessage) {
        super(codeMessage.getMessageKey());
        messageSupport.setFromCodeMessage(codeMessage);
    }

    /**
     * Throwable类型的参数放在最后,不作为组装message的参数
     * @param args
     */
    public SummerException(CodeMessage codeMessage, Object... args) {
        super(codeMessage.getMessageKey());
        messageSupport.setFromCodeMessage(codeMessage);
        messageSupport.setArgs(args);
    }

    public SummerException(CodeMessage codeMessage, Throwable cause) {
        super(codeMessage.getMessageKey(), cause);
        messageSupport.setFromCodeMessage(codeMessage);
    }

    @Override
    public Integer getCode() {
        return messageSupport.getCode();
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