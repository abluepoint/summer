package com.abluepoint.summer.sample.bff.application;


public record BffMcpInvocationIdentity(String initiator, String actor, String effectiveSubject, String transport) {}
