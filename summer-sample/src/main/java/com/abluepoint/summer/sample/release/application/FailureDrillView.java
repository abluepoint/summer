package com.abluepoint.summer.sample.release.application;

import java.util.List;
public record FailureDrillView(String component, String scenario, String authorizationDecision, String executionStatus, boolean retry, boolean automaticRetry, String auditEvidence) {}
