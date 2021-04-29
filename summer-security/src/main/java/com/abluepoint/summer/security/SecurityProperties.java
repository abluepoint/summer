package com.abluepoint.summer.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/*
 * Copyright (c) 2020 abluepoint All Rights Reserved.
 * File:SecurityProperties.java
 * Date:2020-03-23 17:47:23
 */

@ConfigurationProperties(prefix = "summer.security")
public class SecurityProperties {

    private boolean ignoreAll = false;
    private String[] ignored;

    public String[] getIgnored() {
        return ignored;
    }

    public void setIgnored(String[] ignored) {
        this.ignored = ignored;
    }

    public boolean isIgnoreAll() {
        return ignoreAll;
    }

    public void setIgnoreAll(boolean ignoreAll) {
        this.ignoreAll = ignoreAll;
    }
}
