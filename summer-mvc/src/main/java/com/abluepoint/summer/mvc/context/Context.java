package com.abluepoint.summer.mvc.context;

import java.util.Map;

/*
 * Copyright (c) 2020 abluepoint All Rights Reserved.
 * File:Context.java
 * Date:2020-03-23 17:39:23
 */

public interface Context{

    public static final String CONTEXT_ATTR = Context.class.getName() + ".context";
    public static final String DATA_ATTR = Context.class.getName() + ".data";

    Map<String, Object> getData();

    void setData(Map<String, Object> data);
}
