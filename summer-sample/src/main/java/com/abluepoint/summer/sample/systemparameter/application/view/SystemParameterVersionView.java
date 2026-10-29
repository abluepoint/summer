package com.abluepoint.summer.sample.systemparameter.application.view;

import com.abluepoint.summer.sample.systemparameter.domain.SystemParameterReleaseState;
import com.abluepoint.summer.sample.systemparameter.domain.SystemParameterStatus;

import java.time.Instant;

public record SystemParameterVersionView(
        long revision, SystemParameterReleaseState state, String parameterKey, long baseVersion,
        String principal, Instant createdAt, Object value, SystemParameterStatus status,
        long currentVersion) {}
