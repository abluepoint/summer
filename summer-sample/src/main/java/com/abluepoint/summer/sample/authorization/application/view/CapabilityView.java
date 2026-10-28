package com.abluepoint.summer.sample.authorization.application.view;

public record CapabilityView(String capabilityId, String enterpriseId, String scope, String code, String resource,
                     String action, String riskLevel, String status, long version) {}
