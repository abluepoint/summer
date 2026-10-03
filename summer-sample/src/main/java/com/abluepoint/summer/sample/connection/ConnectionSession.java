package com.abluepoint.summer.sample.connection;

import java.time.Instant;

public record ConnectionSession(String enterpriseId, String sessionRef, String status, Instant expiresAt) {}
