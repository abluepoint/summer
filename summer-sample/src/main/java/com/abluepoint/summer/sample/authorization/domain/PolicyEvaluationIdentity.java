package com.abluepoint.summer.sample.authorization.domain;

public record PolicyEvaluationIdentity(String initiator, String actor, String effectiveSubject, String enterpriseId) {}
