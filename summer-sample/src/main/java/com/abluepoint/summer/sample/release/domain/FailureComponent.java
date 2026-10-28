package com.abluepoint.summer.sample.release.domain;
/** Failure drills are fail-closed and never authorize automatic replay. */
public enum FailureComponent {
 IAM, CONNECTION_SERVICE;
 public String authorizationDecision() { return this==IAM?"DENY":null; }
 public String executionStatus() { return this==CONNECTION_SERVICE?"EXECUTION_UNKNOWN":null; }
 public boolean retryAllowed() { return false; }
 public String auditEvidence() { return "REQUIRED"; }
}
