package com.abluepoint.summer.common.exception;

/*
 * Copyright (c) 2020 abluepoint All Rights Reserved.
 * File:MessageSupport.java
 * Date:2020-03-10 00:26:10
 */

public class MessageSupport implements Messageable {
	private static final long serialVersionUID = -979815032339047301L;
	private String messageKey = null;
	private Object[] args;

	public static void exportMessage(Messageable messageable, StringBuilder sb) {
		sb.append(messageable.getMessageKey());
		if (messageable.getArgs() != null) {
			sb.append(" Args:");
			Object[] arrayOfObject = messageable.getArgs();
			int length = arrayOfObject.length;
			for (int i = 0; i < length; i++) {
				sb.append(arrayOfObject[i]).append(" ");
			}
		}
	}

	public void setMessageKey(String messageKey) {
		this.messageKey = messageKey;
	}

	public String getMessageKey() {
		return this.messageKey;
	}

	public void setArgs(Object[] args) {
		this.args = args;
	}

	public Object[] getArgs() {
		return this.args;
	}
}