package com.abluepoint.summer.sample.agent.application;

import com.abluepoint.summer.sample.agent.application.*;
import com.abluepoint.summer.sample.agent.domain.*;
import com.abluepoint.summer.sample.agent.infrastructure.*;


import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import static com.abluepoint.summer.sample.agent.infrastructure.AgentAccess.*;

import java.time.Instant;
import java.util.*;

@Service
public final class AgentService {
    private final AgentStore store = new AgentStore();
    private static final String ENTERPRISE = "ent-acme";

    public synchronized List<AgentView> list(String enterpriseId, Authentication auth) {
        requireAdmin(enterpriseId, auth, "enterprise.agents.read", "enterprise.agents.write");
        return store.agents.values().stream().filter(a -> a.enterpriseId.equals(enterpriseId)).map(this::view).toList();
    }

    public synchronized AgentView create(String enterpriseId, AgentCreateCommand request, String idempotencyKey,
                                   String requestId, Authentication auth) {
        requireAdmin(enterpriseId, auth, "enterprise.agents.write");
        requireKey(idempotencyKey);
        String fingerprint = fingerprint(request);
        AgentReceipt previous = store.receipts.get(op(enterpriseId, idempotencyKey));
        if (previous != null) return replay(previous, fingerprint);
        if (!ENTERPRISE.equals(enterpriseId)) throw new AgentConflictException("Enterprise does not exist");
        if (request == null || blank(request.name()) || blank(request.code())) {
            throw new AgentValidationException("name and code are required");
        }
        if (store.agents.values().stream().anyMatch(a -> a.enterpriseId.equals(enterpriseId) && a.agentCode.equals(request.code().trim()))) {
            throw new AgentConflictException("Agent code already exists");
        }
        String id = "agent-" + UUID.randomUUID().toString().substring(0, 8);
        AgentEntity agent = new AgentEntity(enterpriseId, id, "principal-" + UUID.randomUUID().toString().substring(0, 8),
                request.code().trim(), request.name().trim(), request.description(), request.ownerMemberId(),
                request.runtimeType() == null ? "SERVICE" : request.runtimeType(), "ACTIVE", 1);
        store.agents.put(id, agent);
        AgentView out = view(agent);
        remember(enterpriseId, idempotencyKey, fingerprint, out);
        audit(agent, null, "AGENT_CREATED", requestId, auth);
        return out;
    }

    public synchronized AgentView detail(String enterpriseId, String agentId, Authentication auth) {
        requireAdmin(enterpriseId, auth, "enterprise.agents.read", "enterprise.agents.write");
        return view(agent(enterpriseId, agentId));
    }

    public synchronized AgentView update(String enterpriseId, String agentId, AgentUpdateCommand request, String ifMatch,
                                   String idempotencyKey, String requestId, Authentication auth) {
        requireAdmin(enterpriseId, auth, "enterprise.agents.write");
        requireKey(idempotencyKey);
        String fingerprint = fingerprint(request, ifMatch);
        AgentReceipt previous = store.receipts.get(op(enterpriseId, idempotencyKey));
        if (previous != null) return replay(previous, fingerprint);
        AgentEntity agent = agent(enterpriseId, agentId);
        etag(agent.version, ifMatch);
        if (request == null || blank(request.name())) throw new AgentValidationException("name is required");
        agent.name = AgentRules.name(request.name());
        agent.description = request.description();
        agent.ownerMemberId = request.ownerMemberId();
        if (!blank(request.runtimeType())) agent.runtimeType = request.runtimeType();
        agent.version++;
        AgentView out = view(agent);
        remember(enterpriseId, idempotencyKey, fingerprint, out);
        audit(agent, null, "AGENT_UPDATED", requestId, auth);
        return out;
    }

    public synchronized AgentView setStatus(String enterpriseId, String agentId, String status, String ifMatch,
                                       String idempotencyKey, String requestId, Authentication auth) {
        requireAdmin(enterpriseId, auth, "enterprise.agents.write");
        requireKey(idempotencyKey);
        String fingerprint = fingerprint(status, ifMatch);
        AgentReceipt previous = store.receipts.get(op(enterpriseId, idempotencyKey));
        if (previous != null) return replay(previous, fingerprint);
        AgentEntity agent = agent(enterpriseId, agentId);
        etag(agent.version, ifMatch);
        agent.status = status;
        agent.version++;
        AgentView out = view(agent);
        remember(enterpriseId, idempotencyKey, fingerprint, out);
        audit(agent, null, "AGENT_" + status, requestId, auth);
        return out;
    }

    public synchronized List<AgentInstanceView> instances(String enterpriseId, String agentId, Authentication auth) {
        requireAdmin(enterpriseId, auth, "enterprise.agents.read", "enterprise.agents.write");
        return agent(enterpriseId, agentId).instances.values().stream().map(this::view).toList();
    }

    public synchronized AgentInstanceView registerInstance(AgentInstanceRegisterCommand request, String requestId,
                                                     Authentication auth) {
        JwtAuthenticationToken runtime = requireRuntime(auth);
        String trustedAgentId = claim(runtime, "agent_id");
        String trustedRuntimeId = claim(runtime, "runtime_id");
        String trustedWorkload = claim(runtime, "workload_identity");
        String trustedAttestation = claim(runtime, "attestation_type");
        if (blank(trustedAgentId) || blank(trustedRuntimeId) || blank(trustedWorkload)
                || !runtime.getToken().getClaimAsBoolean("attestation_verified")) {
            throw new AccessDeniedException("Verified workload identity is required");
        }
        if (request == null || blank(request.runtimeId())) throw new AgentValidationException("runtimeId is required");
        if (!trustedRuntimeId.equals(request.runtimeId())) throw new AccessDeniedException("Runtime assertion mismatch");
        if (!blank(request.agentId()) && !trustedAgentId.equals(request.agentId())) {
            throw new AccessDeniedException("Self-reported agent identity is not trusted");
        }
        AgentEntity agent = agentById(trustedAgentId);
        if (!AgentRules.isActive(agent.status)) throw new AccessDeniedException("Agent is not active");
        if (agent.instances.values().stream().anyMatch(i -> i.runtimeId.equals(trustedRuntimeId) && AgentRules.isActive(i.status))) {
            throw new AgentConflictException("Runtime instance already registered");
        }
        Instant now = Instant.now();
        long ttl = AgentRules.lifetime(request.expiresInSeconds(), 900);
        String instanceId = "ainst-" + UUID.randomUUID().toString().substring(0, 8);
        AgentInstanceEntity instance = new AgentInstanceEntity(agent.enterpriseId, instanceId, agent.id, trustedRuntimeId,
                trustedWorkload, trustedAttestation, claim(runtime, "attestation_digest"), request.environment(), "ACTIVE",
                now, now, now.plusSeconds(ttl), 1);
        agent.instances.put(instanceId, instance);
        audit(agent, instance, "AGENT_INSTANCE_REGISTERED", requestId, auth);
        return view(instance);
    }

    public synchronized AgentInstanceView heartbeat(String instanceId, AgentInstanceHeartbeatCommand request,
                                              String requestId, Authentication auth) {
        JwtAuthenticationToken runtime = requireRuntime(auth);
        AgentInstanceEntity instance = findInstance(instanceId);
        assertRuntime(instance, runtime);
        if (!AgentRules.isActive(instance.status)) throw new AccessDeniedException("Agent instance is not active");
        long extension = AgentRules.lifetime(request == null ? null : request.extendSeconds(), 600);
        Instant now = Instant.now();
        instance.lastSeenAt = now;
        instance.expiresAt = now.plusSeconds(extension);
        instance.version++;
        audit(agentById(instance.agentId), instance, "AGENT_INSTANCE_HEARTBEAT", requestId, auth);
        return view(instance);
    }

    public synchronized AgentInstanceView revoke(String enterpriseId, String instanceId, String ifMatch, String idempotencyKey,
                                           String requestId, Authentication auth) {
        requireAdmin(enterpriseId, auth, "enterprise.agents.write");
        requireKey(idempotencyKey);
        String fingerprint = fingerprint(instanceId, ifMatch);
        AgentReceipt previous = store.receipts.get(op(enterpriseId, idempotencyKey));
        if (previous != null) return replay(previous, fingerprint);
        AgentInstanceEntity instance = findInstance(instanceId);
        if (!enterpriseId.equals(instance.enterpriseId)) throw new AccessDeniedException("Enterprise context denied");
        etag(instance.version, ifMatch);
        instance.status = "REVOKED";
        instance.version++;
        AgentInstanceView out = view(instance);
        remember(enterpriseId, idempotencyKey, fingerprint, out);
        audit(agentById(instance.agentId), instance, "AGENT_INSTANCE_REVOKED", requestId, auth);
        return out;
    }

    public synchronized List<AgentAuditView> audit(String enterpriseId, String agentId, Authentication auth) {
        requireAdmin(enterpriseId, auth, "enterprise.agents.read", "enterprise.agents.write");
        agent(enterpriseId, agentId);
        return List.copyOf(store.audits.getOrDefault(enterpriseId + ":" + agentId, List.of()));
    }

    private AgentEntity agent(String enterpriseId, String agentId) {
        if (!ENTERPRISE.equals(enterpriseId)) throw new AccessDeniedException("Enterprise context denied");
        AgentEntity agent = store.agents.get(agentId);
        if (agent == null || !enterpriseId.equals(agent.enterpriseId)) throw new AgentConflictException("Agent not found");
        return agent;
    }

    private AgentEntity agentById(String agentId) {
        AgentEntity agent = store.agents.get(agentId);
        if (agent == null) throw new AgentConflictException("Agent not found");
        return agent;
    }

    private AgentInstanceEntity findInstance(String instanceId) {
        return store.agents.values().stream().map(a -> a.instances.get(instanceId)).filter(Objects::nonNull).findFirst()
                .orElseThrow(() -> new AgentConflictException("Agent instance not found"));
    }

    private void etag(long version, String ifMatch) { AgentRules.requireVersion(version, ifMatch); }
    private void requireKey(String key) { if (blank(key)) throw new AgentValidationException("Idempotency-Key is required"); }
    private static boolean blank(String value) { return value == null || value.isBlank(); }
    private static String claim(JwtAuthenticationToken jwt, String name) { return jwt.getToken().getClaimAsString(name); }
    private static String fingerprint(Object... values) { return Integer.toHexString(Arrays.deepToString(values).replace("Command[", "Request[").hashCode()); }
    private static String op(String enterpriseId, String key) { return enterpriseId + ":" + key; }

    @SuppressWarnings("unchecked") private <T> T replay(AgentReceipt receipt, String fingerprint) {
        if (!receipt.fingerprint().equals(fingerprint)) throw new AgentIdempotencyConflictException("Idempotency key reused");
        return (T) receipt.result();
    }
    private void remember(String enterpriseId, String key, String fingerprint, Object result) { store.receipts.put(op(enterpriseId, key), new AgentReceipt(fingerprint, result)); }

    private void audit(AgentEntity agent, AgentInstanceEntity instance, String action, String requestId, Authentication auth) {
        store.audits.computeIfAbsent(agent.enterpriseId + ":" + agent.id, k -> new ArrayList<>())
                .add(new AgentAuditView(action, agent.enterpriseId, agent.id, instance == null ? null : instance.id,
                        auth == null ? "" : auth.getName(), requestId == null ? "" : requestId, Instant.now()));
    }

    private AgentView view(AgentEntity agent) {
        int active = (int) agent.instances.values().stream().filter(i -> AgentRules.isActive(i.status)).count();
        return new AgentView(agent.enterpriseId, agent.id, agent.principalId, agent.agentCode, agent.name, agent.description,
                agent.ownerMemberId, agent.runtimeType, agent.status, agent.version, active);
    }
    private AgentInstanceView view(AgentInstanceEntity i) {
        return new AgentInstanceView(i.enterpriseId, i.id, i.agentId, i.runtimeId, i.workloadIdentity, i.attestationType,
                i.attestationDigest, i.environment, i.status, i.startedAt, i.lastSeenAt, i.expiresAt, i.version);
    }

}
