/*
 * Copyright (c) 2020 abluepoint All Rights Reserved.
 * File:SummerRuntimeException.java
 * Date:2020-11-12 17:44:12
 */

package com.abluepoint.summer.common.exception;

public class SummerRuntimeException extends RuntimeException implements Messageable {

    private static final long serialVersionUID = 8029850269267798830L;
    private MessageSupport messageSupport = new MessageSupport();

    public SummerRuntimeException() {
        super();
        messageSupport.setFromCodeMessage(CodeMessageEnum.SYSTEM_UNDEFINED_ERROR);
    }

    public SummerRuntimeException(CodeMessage codeMessage) {
        super(codeMessage.getMessageKey());
        messageSupport.setFromCodeMessage(codeMessage);
    }

    public SummerRuntimeException(CodeMessage codeMessage, Object... args) {
        super(codeMessage.getMessageKey());
        messageSupport.setFromCodeMessage(codeMessage);
        messageSupport.setArgs(args);
    }

    public SummerRuntimeException(CodeMessage codeMessage, Throwable cause) {
        super(codeMessage.getMessageKey(), cause);
        messageSupport.setFromCodeMessage(codeMessage);
    }

    public SummerRuntimeException(SummerException e) {
        super(e.getMessageKey(), e);
        messageSupport.setCode(e.getCode());
        messageSupport.setMessageKey(e.getMessageKey());
        messageSupport.setArgs(e.getArgs());
    }


    public SummerRuntimeException(Throwable cause) {
        super(cause.getMessage(), cause);
        if (cause instanceof Messageable) {
            messageSupport.setFromCodeMessage((Messageable) cause);
            messageSupport.setArgs(((Messageable) cause).getArgs());
        } else {
            messageSupport.setFromCodeMessage(CodeMessageEnum.SYSTEM_UNDEFINED_ERROR);
            messageSupport.setMessageKey(cause.getMessage());
        }
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

    public MessageSupport getMessageSupport() {
        return messageSupport;
    }

}