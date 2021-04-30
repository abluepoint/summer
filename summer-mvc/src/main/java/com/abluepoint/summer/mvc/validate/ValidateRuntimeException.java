/*
 * Copyright (c) 2020 abluepoint All Rights Reserved.
 * File:ValidateRuntimeException.java
 * Date:2020-11-12 17:44:12
 */

package com.abluepoint.summer.mvc.validate;

import com.abluepoint.summer.common.exception.SummerRuntimeException;

public class ValidateRuntimeException extends SummerRuntimeException {

	private static final long serialVersionUID = 2285211535401698199L;

	public ValidateRuntimeException() {
		super();
	}

	public ValidateRuntimeException(String message) {
		super(message);
	}

	public ValidateRuntimeException(String message, Object... args) {
		super(message,args);
	}

	public ValidateRuntimeException(String message, Throwable cause) {
		super(message, cause);
	}

	public ValidateRuntimeException(String message, Throwable cause, Object... args) {
		super(message,cause,args);
	}
	
	public ValidateRuntimeException(Throwable cause) {
		super(cause);
	}
	
}