package com.abluepoint.summer.mvc.context;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;

/*
 * Copyright (c) 2020 abluepoint All Rights Reserved.
 * File:DefaultContext.java
 * Date:2020-03-23 17:39:23
 */

public class DefaultContext implements Context {


    private Map<String, Object> data;

    @Override
    public Map<String, Object> getData() {
        return data;
    }

    @Override
    public void setData(Map<String, Object> data) {
        this.data = data;
    }

}
