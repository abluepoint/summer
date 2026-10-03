package com.abluepoint.summer.sample.agent;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
final class AgentService {
    private static final String ENTERPRISE = "ent-acme";
    private static final String REGISTER_SCOPE = "internal.agent_instances.write";
    private final Map<String, AgentRecord> agents = new LinkedHashMap<>();
    private final Map<String, Receipt> receipts = new HashMap<>();
    private final Map<String, List<AgentAuditView>> audits = new HashMap<>();

    synchronized List<AgentView> list(String enterpriseId, Authentication auth) {
        requireAdmin(enterpriseId, auth, "enterprise.agents.read", "enterprise.agents.write");
        return agents.values().stream().filter(a -> a.enterpriseId.equals(enterpriseId)).map(this::view).toList();
    }

    synchronized AgentView create(String enterpriseId, AgentCreateRequest request, String idempotencyKey,
                                   String requestId, Authentication auth) {
        requireAdmin(enterpriseId, auth, "enterprise.agents.write");
        requireKey(idempotencyKey);
        String fingerprint = fingerprint(request);
        Receipt previous = receipts.get(op(enterpriseId, idempotencyKey));
        if (previous != null) return replay(previous, fingerprint);
        if (!ENTERPRISE.equals(enterpriseId)) throw new AgentConflictException("Enterprise does not exist");
        if (request == null || blank(request.name()) || blank(request.code())) {
            throw new AgentValidationException("name and code are required");
        }
        if (agents.values().stream().anyMatch(a -> a.enterpriseId.equals(enterpriseId) && a.agentCode.equals(request.code().trim()))) {
            throw new AgentConflictException("Agent code already exists");
        }
        String id = "agent-" + UUID.randomUUID().toString().substring(0, 8);
        AgentRecord agent = new AgentRecord(enterpriseId, id, "principal-" + UUID.randomUUID().toString().substring(0, 8),
                request.code().trim(), request.name().trim(), request.description(), request.ownerMemberId(),
                request.runtimeType() == null ? "SERVICE" : request.runtimeType(), "ACTIVE", 1);
        agents.put(id, agent);
        AgentView out = view(agent);
        remember(enterpriseId, idempotencyKey, fingerprint, out);
        audit(agent, null, "AGENT_CREATED", requestId, auth);
        return out;
    }

    synchronized AgentView detail(String enterpriseId, String agentId, Authentication auth) {
        requireAdmin(enterpriseId, auth, "enterprise.agents.read", "enterprise.agents.write");
        return view(agent(enterpriseId, agentId));
    }

    synchronized AgentView update(String enterpriseId, String agentId, AgentUpdateRequest request, String ifMatch,
                                   String idempotencyKey, String requestId, Authentication auth) {
        requireAdmin(enterpriseId, auth, "enterprise.agents.write");
        requireKey(idempotencyKey);
        String fingerprint = fingerprint(request, ifMatch);
        Receipt previous = receipts.get(op(enterpriseId, idempotencyKey));
        if (previous != null) return replay(previous, fingerprint);
        AgentRecord agent = agent(enterpriseId, agentId);
        etag(agent.version, ifMatch);
        if (request == null || blank(request.name())) throw new AgentValidationException("name is required");
        agent.name = request.name().trim();
        agent.description = request.description();
        agent.ownerMemberId = request.ownerMemberId();
        if (!blank(request.runtimeType())) agent.runtimeType = request.runtimeType();
        agent.version++;
        AgentView out = view(agent);
        remember(enterpriseId, idempotencyKey, fingerprint, out);
        audit(agent, null, "AGENT_UPDATED", requestId, auth);
        return out;
    }

    synchronized AgentView setStatus(String enterpriseId, String agentId, String status, String ifMatch,
                                       String idempotencyKey, String requestId, Authentication auth) {
        requireAdmin(enterpriseId, auth, "enterprise.agents.write");
        requireKey(idempotencyKey);
        String fingerprint = fingerprint(status, ifMatch);
        Receipt previous = receipts.get(op(enterpriseId, idempotencyKey));
        if (previous != null) return replay(previous, fingerprint);
        AgentRecord agent = agent(enterpriseId, agentId);
        etag(agent.version, ifMatch);
        agent.status = status;
        agent.version++;
        AgentView out = view(agent);
        remember(enterpriseId, idempotencyKey, fingerprint, out);
        audit(agent, null, "AGENT_" + status, requestId, auth);
        return out;
    }

    synchronized List<AgentInstanceView> instances(String enterpriseId, String agentId, Authentication auth) {
        requireAdmin(enterpriseId, auth, "enterprise.agents.read", "enterprise.agents.write");
        return agent(enterpriseId, agentId).instances.values().stream().map(this::view).toList();
    }

    synchronized AgentInstanceView registerInstance(AgentInstanceRegisterRequest request, String requestId,
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
        AgentRecord agent = agentById(trustedAgentId);
        if (!"ACTIVE".equals(agent.status)) throw new AccessDeniedException("Agent is not active");
        if (agent.instances.values().stream().anyMatch(i -> i.runtimeId.equals(trustedRuntimeId) && "ACTIVE".equals(i.status))) {
            throw new AgentConflictException("Runtime instance already registered");
        }
        Instant now = Instant.now();
        long ttl = request.expiresInSeconds() == null ? 900 : Math.max(60, Math.min(3600, request.expiresInSeconds()));
        String instanceId = "ainst-" + UUID.randomUUID().toString().substring(0, 8);
        AgentInstanceRecord instance = new AgentInstanceRecord(agent.enterpriseId, instanceId, agent.id, trustedRuntimeId,
                trustedWorkload, trustedAttestation, claim(runtime, "attestation_digest"), request.environment(), "ACTIVE",
                now, now, now.plusSeconds(ttl), 1);
        agent.instances.put(instanceId, instance);
        audit(agent, instance, "AGENT_INSTANCE_REGISTERED", requestId, auth);
        return view(instance);
    }

    synchronized AgentInstanceView heartbeat(String instanceId, AgentInstanceHeartbeatRequest request,
                                              String requestId, Authentication auth) {
        JwtAuthenticationToken runtime = requireRuntime(auth);
        AgentInstanceRecord instance = findInstance(instanceId);
        assertRuntime(instance, runtime);
        if (!"ACTIVE".equals(instance.status)) throw new AccessDeniedException("Agent instance is not active");
        long extension = request == null || request.extendSeconds() == null ? 600 : Math.max(60, Math.min(3600, request.extendSeconds()));
        Instant now = Instant.now();
        instance.lastSeenAt = now;
        instance.expiresAt = now.plusSeconds(extension);
        instance.version++;
        audit(agentById(instance.agentId), instance, "AGENT_INSTANCE_HEARTBEAT", requestId, auth);
        return view(instance);
    }

    synchronized AgentInstanceView revoke(String enterpriseId, String instanceId, String ifMatch, String idempotencyKey,
                                           String requestId, Authentication auth) {
        requireAdmin(enterpriseId, auth, "enterprise.agents.write");
        requireKey(idempotencyKey);
        String fingerprint = fingerprint(instanceId, ifMatch);
        Receipt previous = receipts.get(op(enterpriseId, idempotencyKey));
        if (previous != null) return replay(previous, fingerprint);
        AgentInstanceRecord instance = findInstance(instanceId);
        if (!enterpriseId.equals(instance.enterpriseId)) throw new AccessDeniedException("Enterprise context denied");
        etag(instance.version, ifMatch);
        instance.status = "REVOKED";
        instance.version++;
        AgentInstanceView out = view(instance);
        remember(enterpriseId, idempotencyKey, fingerprint, out);
        audit(agentById(instance.agentId), instance, "AGENT_INSTANCE_REVOKED", requestId, auth);
        return out;
    }

    synchronized List<AgentAuditView> audit(String enterpriseId, String agentId, Authentication auth) {
        requireAdmin(enterpriseId, auth, "enterprise.agents.read", "enterprise.agents.write");
        agent(enterpriseId, agentId);
        return List.copyOf(audits.getOrDefault(enterpriseId + ":" + agentId, List.of()));
    }

    private JwtAuthenticationToken requireRuntime(Authentication auth) {
        if (!(auth instanceof JwtAuthenticationToken jwt)
                || jwt.getAuthorities().stream().noneMatch(a -> ("SCOPE_" + REGISTER_SCOPE).equals(a.getAuthority()))) {
            throw new AccessDeniedException("Runtime scope denied");
        }
        return jwt;
    }

    private void assertRuntime(AgentInstanceRecord instance, JwtAuthenticationToken runtime) {
        if (!instance.agentId.equals(claim(runtime, "agent_id"))
                || !instance.runtimeId.equals(claim(runtime, "runtime_id"))
                || !instance.id.equals(claim(runtime, "agent_instance_id"))
                || !instance.workloadIdentity.equals(claim(runtime, "workload_identity"))
                || !Boolean.TRUE.equals(runtime.getToken().getClaimAsBoolean("attestation_verified"))) {
            throw new AccessDeniedException("Runtime identity assertion mismatch");
        }
    }

    private void requireAdmin(String enterpriseId, Authentication auth, String... capabilities) {
        if (!(auth instanceof JwtAuthenticationToken jwt) || !activeMembership(jwt, enterpriseId)
                || !hasCapability(jwt, enterpriseId, capabilities)
                || Arrays.stream(capabilities).noneMatch(c -> jwt.getAuthorities().stream().anyMatch(a -> ("SCOPE_" + c).equals(a.getAuthority())))) {
            throw new AccessDeniedException("Agent capability denied");
        }
    }

    private boolean activeMembership(JwtAuthenticationToken jwt, String enterpriseId) {
        Object memberships = jwt.getToken().getClaims().get("enterprise_memberships");
        return memberships instanceof Collection<?> values && values.stream().filter(Map.class::isInstance).map(Map.class::cast)
                .anyMatch(m -> enterpriseId.equals(String.valueOf(m.getOrDefault("enterpriseId", m.get("enterprise_id"))))
                        && "ACTIVE".equalsIgnoreCase(String.valueOf(m.getOrDefault("status", m.get("membership_status")))));
    }

    private boolean hasCapability(JwtAuthenticationToken jwt, String enterpriseId, String... capabilities) {
        Object value = jwt.getToken().getClaims().get("enterprise_capabilities");
        if (!(value instanceof Map<?, ?> map)) return false;
        Object granted = map.get(enterpriseId);
        return granted instanceof Collection<?> values && values.stream().map(String::valueOf).anyMatch(v -> Arrays.asList(capabilities).contains(v));
    }

    private AgentRecord agent(String enterpriseId, String agentId) {
        if (!ENTERPRISE.equals(enterpriseId)) throw new AccessDeniedException("Enterprise context denied");
        AgentRecord agent = agents.get(agentId);
        if (agent == null || !enterpriseId.equals(agent.enterpriseId)) throw new AgentConflictException("Agent not found");
        return agent;
    }

    private AgentRecord agentById(String agentId) {
        AgentRecord agent = agents.get(agentId);
        if (agent == null) throw new AgentConflictException("Agent not found");
        return agent;
    }

    private AgentInstanceRecord findInstance(String instanceId) {
        return agents.values().stream().map(a -> a.instances.get(instanceId)).filter(Objects::nonNull).findFirst()
                .orElseThrow(() -> new AgentConflictException("Agent instance not found"));
    }

    private void etag(long version, String ifMatch) {
        if (ifMatch == null || !ifMatch.equals("\"" + version + "\"")) throw new AgentPreconditionException("The resource has changed");
    }

    private void requireKey(String key) { if (blank(key)) throw new AgentValidationException("Idempotency-Key is required"); }
    private static boolean blank(String value) { return value == null || value.isBlank(); }
    private static String claim(JwtAuthenticationToken jwt, String name) { return jwt.getToken().getClaimAsString(name); }
    private static String fingerprint(Object... values) { return Integer.toHexString(Arrays.deepToString(values).hashCode()); }
    private static String op(String enterpriseId, String key) { return enterpriseId + ":" + key; }

    @SuppressWarnings("unchecked") private <T> T replay(Receipt receipt, String fingerprint) {
        if (!receipt.fingerprint.equals(fingerprint)) throw new AgentIdempotencyConflictException("Idempotency key reused");
        return (T) receipt.result;
    }
    private void remember(String enterpriseId, String key, String fingerprint, Object result) { receipts.put(op(enterpriseId, key), new Receipt(fingerprint, result)); }

    private void audit(AgentRecord agent, AgentInstanceRecord instance, String action, String requestId, Authentication auth) {
        audits.computeIfAbsent(agent.enterpriseId + ":" + agent.id, k -> new ArrayList<>())
                .add(new AgentAuditView(action, agent.enterpriseId, agent.id, instance == null ? null : instance.id,
                        auth == null ? "" : auth.getName(), requestId == null ? "" : requestId, Instant.now()));
    }

    private AgentView view(AgentRecord agent) {
        int active = (int) agent.instances.values().stream().filter(i -> "ACTIVE".equals(i.status)).count();
        return new AgentView(agent.enterpriseId, agent.id, agent.principalId, agent.agentCode, agent.name, agent.description,
                agent.ownerMemberId, agent.runtimeType, agent.status, agent.version, active);
    }
    private AgentInstanceView view(AgentInstanceRecord i) {
        return new AgentInstanceView(i.enterpriseId, i.id, i.agentId, i.runtimeId, i.workloadIdentity, i.attestationType,
                i.attestationDigest, i.environment, i.status, i.startedAt, i.lastSeenAt, i.expiresAt, i.version);
    }

    private record Receipt(String fingerprint, Object result) {}
    private static final class AgentRecord {
        final String enterpriseId, id, principalId, agentCode;
        String name, description, ownerMemberId, runtimeType, status;
        long version;
        final Map<String, AgentInstanceRecord> instances = new LinkedHashMap<>();
        AgentRecord(String enterpriseId, String id, String principalId, String agentCode, String name, String description,
                    String ownerMemberId, String runtimeType, String status, long version) {
            this.enterpriseId = enterpriseId; this.id = id; this.principalId = principalId; this.agentCode = agentCode;
            this.name = name; this.description = description; this.ownerMemberId = ownerMemberId;
            this.runtimeType = runtimeType; this.status = status; this.version = version;
        }
    }
    private static final class AgentInstanceRecord {
        final String enterpriseId, id, agentId, runtimeId, workloadIdentity, attestationType, attestationDigest, environment;
        String status; final Instant startedAt; Instant lastSeenAt, expiresAt; long version;
        AgentInstanceRecord(String enterpriseId, String id, String agentId, String runtimeId, String workloadIdentity,
                            String attestationType, String attestationDigest, String environment, String status,
                            Instant startedAt, Instant lastSeenAt, Instant expiresAt, long version) {
            this.enterpriseId = enterpriseId; this.id = id; this.agentId = agentId; this.runtimeId = runtimeId;
            this.workloadIdentity = workloadIdentity; this.attestationType = attestationType; this.attestationDigest = attestationDigest;
            this.environment = environment; this.status = status; this.startedAt = startedAt; this.lastSeenAt = lastSeenAt;
            this.expiresAt = expiresAt; this.version = version;
        }
    }
}
