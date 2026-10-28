package com.abluepoint.summer.sample.organization.application;
import java.util.*;
import java.time.Instant;

public record OrganizationAuditView(String actorSubject, String enterpriseId, String resourceId, String action, String beforeStatus, String afterStatus, String requestId) {}
