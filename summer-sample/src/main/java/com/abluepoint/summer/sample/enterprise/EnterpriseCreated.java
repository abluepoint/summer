package com.abluepoint.summer.sample.enterprise;

/** Synchronous onboarding notification; every listener participates in the creation transaction. */
public record EnterpriseCreated(String enterpriseId, String creatorSubject, String requestId) {}
