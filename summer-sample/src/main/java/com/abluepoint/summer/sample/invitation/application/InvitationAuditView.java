package com.abluepoint.summer.sample.invitation.application;
import java.util.*;
import java.time.Instant;

public record InvitationAuditView(String actorSubject, String enterpriseId, String resourceId, String action, String beforeStatus, String afterStatus, String requestId) {}
