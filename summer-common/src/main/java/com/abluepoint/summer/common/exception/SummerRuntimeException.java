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
		messageSupport.setMessageKey("");
	}

	public SummerRuntimeException(String message) {
		super(message);
		messageSupport.setMessageKey(message);
	}

	public SummerRuntimeException(String message, Object... args) {
		super(message);
		messageSupport.setMessageKey(message);
		messageSupport.setArgs(args);
	}

	public SummerRuntimeException(String message, Throwable cause) {
		super(message, cause);
		messageSupport.setMessageKey(message);
	}

	public SummerRuntimeException(Throwable cause,String message, Object... args) {
		super(message, cause);
		messageSupport.setMessageKey(message);
		messageSupport.setArgs(args);
	}

	public SummerRuntimeException(SummerException e) {
		super(e.getMessageKey(), e);
		messageSupport.setMessageKey(e.getMessageKey());
		messageSupport.setArgs(e.getArgs());

	}

	public SummerRuntimeException(Throwable cause) {
		super(cause.getMessage(), cause);
		if (cause instanceof Messageable) {
			messageSupport.setMessageKey(((Messageable) cause).getMessageKey());
			messageSupport.setArgs(((Messageable) cause).getArgs());
		} else {
			messageSupport.setMessageKey(cause.getMessage());
		}
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