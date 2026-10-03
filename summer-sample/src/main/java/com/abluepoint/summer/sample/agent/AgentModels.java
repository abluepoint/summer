package com.abluepoint.summer.sample.agent;

import java.time.Instant;
import java.util.List;

record AgentCreateRequest(String name, String code, String description, String ownerMemberId, String runtimeType) {}

record AgentUpdateRequest(String name, String description, String ownerMemberId, String runtimeType) {}

record AgentInstanceRegisterRequest(
        String agentId,
        String runtimeId,
        String workloadIdentity,
        String attestationType,
        String attestationDigest,
        String environment,
        Long expiresInSeconds) {}

record AgentInstanceHeartbeatRequest(Long extendSeconds) {}

record AgentView(
        String enterpriseId,
        String agentId,
        String principalId,
        String agentCode,
        String name,
        String description,
        String ownerMemberId,
        String runtimeType,
        String status,
        long version,
        int activeInstanceCount) {}

record AgentInstanceView(
        String enterpriseId,
        String instanceId,
        String agentId,
        String runtimeId,
        String workloadIdentity,
        String attestationType,
        String attestationDigest,
        String environment,
        String status,
        Instant startedAt,
        Instant lastSeenAt,
        Instant expiresAt,
        long version) {}

record AgentRuntimeAssertion(
        String agentId,
        String instanceId,
        String runtimeId,
        String workloadIdentity,
        String attestationType,
        boolean attestationVerified) {}

record AgentAuditView(
        String action,
        String enterpriseId,
        String agentId,
        String instanceId,
        String principal,
        String requestId,
        Instant occurredAt) {}
