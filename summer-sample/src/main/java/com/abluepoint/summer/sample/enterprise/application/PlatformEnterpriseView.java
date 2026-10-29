package com.abluepoint.summer.sample.enterprise.application;

import java.util.List;

public record PlatformEnterpriseView(
        String enterpriseId,
        String name,
        String did,
        String license,
        String status,
        String qualification,
        String resources,
        String activity,
        String revision) {}
