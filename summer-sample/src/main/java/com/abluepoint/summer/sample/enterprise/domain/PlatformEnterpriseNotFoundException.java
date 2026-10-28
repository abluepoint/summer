package com.abluepoint.summer.sample.enterprise.domain;

public class PlatformEnterpriseNotFoundException extends RuntimeException {
    public PlatformEnterpriseNotFoundException(String id) {
        super("Enterprise not found: " + id);
    }
}

