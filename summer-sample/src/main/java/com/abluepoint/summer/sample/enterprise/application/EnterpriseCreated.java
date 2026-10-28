package com.abluepoint.summer.sample.enterprise.application;

/** Synchronous onboarding notification; every listener participates in the creation transaction. */
@org.springframework.modulith.NamedInterface("events")
public record EnterpriseCreated(String enterpriseId, String creatorSubject, String requestId) {}
