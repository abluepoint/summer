package com.abluepoint.summer.sample.connection.domain;


import java.time.Instant;

@org.springframework.modulith.NamedInterface("execution")
public record ConnectionSession(String enterpriseId, String sessionRef, String status, Instant expiresAt) {}
