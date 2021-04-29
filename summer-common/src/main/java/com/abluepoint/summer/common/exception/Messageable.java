package com.abluepoint.summer.common.exception;

/*
 * Copyright (c) 2020 abluepoint All Rights Reserved.
 * File:Messageable.java
 * Date:2020-03-10 00:26:10
 */

import java.io.Serializable;

public interface Messageable extends Serializable {
	public String getMessageKey();
	public Object[] getArgs();
}