package com.abluepoint.summer.sample.member.application;

public record MemberAuditView(String actorSubject, String enterpriseId, String memberId, String action, String beforeStatus, String afterStatus, String requestId) {}
