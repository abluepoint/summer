package com.abluepoint.summer.sample.enterprise.application;

public record EnterpriseAuditView(String actorSubject, String action, String beforeStatus, String afterStatus, String requestId) {}
