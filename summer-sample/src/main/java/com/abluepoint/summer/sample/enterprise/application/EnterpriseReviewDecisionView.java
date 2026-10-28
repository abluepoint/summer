package com.abluepoint.summer.sample.enterprise.application;

public record EnterpriseReviewDecisionView(String actorSubject, String applicationId, String decision, String reason, String beforeStatus, String afterStatus, String requestId) {
    // Existing revision hashes use the historical record rendering.
    @Override public String toString() { return "ReviewDecision[" + "actorSubject=" + actorSubject + ", " + "applicationId=" + applicationId + ", " + "decision=" + decision + ", " + "reason=" + reason + ", " + "beforeStatus=" + beforeStatus + ", " + "afterStatus=" + afterStatus + ", " + "requestId=" + requestId + "]"; }
}
