package com.abluepoint.summer.sample.application.application;

import java.util.List;
import java.time.Instant;

public record CredentialView(String credentialId, int version, String status, String createdAt, String revokedAt, String clientSecret) {}
