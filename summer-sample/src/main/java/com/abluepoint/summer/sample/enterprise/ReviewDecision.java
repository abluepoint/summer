package com.abluepoint.summer.sample.enterprise;

record ReviewDecision(String actorSubject, String applicationId, String decision, String reason, String beforeStatus, String afterStatus, String requestId) {}
