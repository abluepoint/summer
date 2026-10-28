package com.abluepoint.summer.sample.delegation.application;


import java.time.Instant;
import java.util.List;

public record DelegationAuditView(String action,String delegationId,String approvalId,String enterpriseId,String principal,Instant occurredAt) {}
