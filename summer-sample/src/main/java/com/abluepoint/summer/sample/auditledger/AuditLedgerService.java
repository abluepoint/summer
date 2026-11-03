package com.abluepoint.summer.sample.auditledger;

import com.abluepoint.summer.common.error.SummerException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.time.Instant;
import java.util.*;

@Service
public final class AuditLedgerService {
    private static final String ENTERPRISE = "ent-acme";
    private final Map<String, AuditEventView> events = new LinkedHashMap<>();
    private final Map<String, String> idempotency = new HashMap<>();
    private final Map<String, McpInvocationView> invocations = new LinkedHashMap<>();
    private final AuditLedgerPersistenceAdapter persistence;

    /** Keeps direct construction and the sample's in-memory behavior compatible. */
    public AuditLedgerService() {
        this((event, key, fingerprint) -> { });
    }

    public AuditLedgerService(AuditLedgerPersistenceAdapter persistence) {
        this.persistence = Objects.requireNonNull(persistence, "persistence");
    }

    @Autowired
    AuditLedgerService(ObjectProvider<AuditLedgerPersistenceAdapter> provider) {
        this(provider.getIfAvailable(() -> (event, key, fingerprint) -> { }));
    }

    public synchronized void recordExecution(ExecutionEvidence evidence, Authentication authentication) {
        requireService(authentication);
        if (evidence == null || blank(evidence.enterpriseId()) || blank(evidence.invocationId()) || blank(evidence.attemptId())
                || blank(evidence.resultStatus()) || blank(evidence.resultDigest())) {
            throw SummerException.withMessage(AuditLedgerErrorCode.INVALID, "Execution evidence fields are required");
        }
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("resultStatus", evidence.resultStatus());
        detail.put("resultDigest", evidence.resultDigest());
        detail.put("remoteRequestId", evidence.remoteRequestId());
        detail.put("latencyMs", evidence.latencyMs());
        detail.put("connectionSessionRef", evidence.connectionSessionRef());
        AuditEventAppendRequest event = new AuditEventAppendRequest(
                "physical-execution-" + evidence.invocationId() + "-" + evidence.attemptId(),
                "MCP_EXECUTION_COMPLETED", evidence.enterpriseId(), evidence.invocationId(), evidence.invocationId(),
                evidence.invocationId(), evidence.attemptId(), evidence.initiator(), evidence.actor(), evidence.effectiveSubject(),
                evidence.agentInstanceId(), evidence.policyVersion(), evidence.connectionRevision(), evidence.permitId(),
                evidence.permitJti(), evidence.toolId(), evidence.resource(), evidence.action(), evidence.decision(), evidence.reasonCode(), detail);
        append(event, "physical-execution:" + evidence.invocationId() + ":" + evidence.attemptId(), evidence.invocationId(), authentication);
    }

    synchronized AuditEventView append(AuditEventAppendRequest r, String key, String requestId, Authentication a) {
        requireService(a); requireKey(key);
        if (r == null || blank(r.eventId()) || blank(r.eventType()) || blank(r.enterpriseId()) || blank(r.invocationId())) throw SummerException.withMessage(AuditLedgerErrorCode.INVALID, "eventId, eventType, enterpriseId and invocationId are required");
        String fp = Objects.toString(r.eventType()) + Objects.toString(r.detail());
        if (idempotency.containsKey(key)) { if (!idempotency.get(key).equals(fp)) throw SummerException.withMessage(AuditLedgerErrorCode.IDEMPOTENCY_CONFLICT, "Idempotency key reused"); return events.get(idempotency.get(key)); }
        AuditEventView prior = events.get(r.eventId());
        if (prior != null) { if (!Objects.equals(prior.detail(), redact(r.detail()))) throw SummerException.withMessage(AuditLedgerErrorCode.EVENT_IMMUTABLE, "Event is immutable"); return prior; }
        Instant now = Instant.now();
        AuditEventView v = new AuditEventView(r.eventId(), r.eventType(), r.enterpriseId(), r.requestId(), r.traceId(), r.invocationId(), r.attemptId(), r.initiator(), r.actor(), r.effectiveSubject(), r.agentInstanceId(), r.policyVersion(), r.connectionRevision(), r.permitId(), r.permitJti(), r.toolId(), r.resource(), r.action(), r.decision(), r.reasonCode(), redact(r.detail()), now, null, Integer.toHexString(fp.hashCode()));
        persistence.appendAndEnqueue(v, key, fp);
        events.put(v.eventId(), v); idempotency.put(key, v.eventId()); rebuild(v); return v;
    }

    synchronized McpInvocationView complete(String id, CompletionRequest r, String key, String requestId, Authentication a) {
        requireService(a); requireKey(key); if (r == null || blank(r.resultStatus()) || blank(r.resultDigest())) throw SummerException.withMessage(AuditLedgerErrorCode.INVALID, "resultStatus and resultDigest are required");
        McpInvocationView current = invocations.get(id); if (current != null && current.resultDigest() != null) { if ("EXECUTION_UNKNOWN".equals(current.resultStatus())) { /* reconciliation may replace unknown */ } else if (!current.resultDigest().equals(r.resultDigest())) { anomaly(id, "digest contradiction"); throw SummerException.withMessage(AuditLedgerErrorCode.CONTRADICTORY_RECEIPT, "Conflicting result digest"); } else return current; }
        Map<String,Object> resultDetail = new LinkedHashMap<>(); resultDetail.put("resultStatus", r.resultStatus()); resultDetail.put("resultDigest", r.resultDigest()); resultDetail.put("remoteRequestId", r.remoteRequestId()); resultDetail.put("latencyMs", r.latencyMs());
        AuditEventAppendRequest e = new AuditEventAppendRequest("completion-" + id + "-" + r.executionAttemptId(), "MCP_EXECUTION_COMPLETED", ENTERPRISE, requestId, requestId, id, r.executionAttemptId(), null, null, null, null, null, null, r.permitId(), r.permitJti(), null, null, null, null, null, resultDetail);
        append(e, key, requestId, a); return invocations.get(id);
    }
    synchronized McpInvocationView reconcile(String id, ReconciliationRequest r, String key, String requestId, Authentication a) { requireService(a); requireKey(key); McpInvocationView c = invocations.get(id); if (c == null) throw SummerException.withMessage(AuditLedgerErrorCode.CONFLICT, "Invocation not found"); Map<String,Object> detail = new LinkedHashMap<>(); detail.put("resultStatus", r.resultStatus()); detail.put("resultDigest", r.resultDigest()); detail.put("remoteRequestId", r.remoteRequestId()); detail.put("latencyMs", r.latencyMs()); AuditEventAppendRequest e = new AuditEventAppendRequest("reconcile-"+id+"-"+r.executionAttemptId(), "MCP_EXECUTION_RECONCILED", c.enterpriseId(), requestId, requestId, id, r.executionAttemptId(), c.initiator(), c.actor(), c.effectiveSubject(), c.agentInstanceId(), c.policyVersion(), null, c.permitId(), c.permitJti(), c.toolId(), c.resource(), c.action(), c.decision(), c.reasonCode(), detail); append(e,key,requestId,a); return invocations.get(id); }
    synchronized McpInvocationView markUnknown(String e, String id, String key, String rid, Authentication a) { requireAdmin(e,a); McpInvocationView c=invocations.get(id); if(c==null) throw SummerException.withMessage(AuditLedgerErrorCode.CONFLICT, "Invocation not found"); return c; }
    synchronized List<AuditEventView> events(String e, AuditQuery q, Authentication a) { requireAdmin(e,a); return events.values().stream().filter(v->e.equals(v.enterpriseId())).filter(v->q==null||q.matches(v)).toList(); }
    synchronized AuditEventPage queryEvents(String enterpriseId, AuditQuery query, int page, int size, Authentication authentication) {
        requireAdmin(enterpriseId, authentication);
        if (page < 0 || size < 1 || size > 100) {
            throw SummerException.withMessage(AuditLedgerErrorCode.INVALID, "page must be non-negative and size must be between 1 and 100");
        }
        List<AuditEventView> filtered = events.values().stream()
                .filter(event -> enterpriseId.equals(event.enterpriseId()))
                .filter(event -> query == null || query.matches(event))
                .sorted(Comparator.comparing(AuditEventView::occurredAt).reversed()
                        .thenComparing(AuditEventView::eventId))
                .toList();
        long offset = (long) page * size;
        int start = offset >= filtered.size() ? filtered.size() : (int) offset;
        int end = Math.min(start + size, filtered.size());
        int totalPages = filtered.isEmpty() ? 0 : (int) Math.ceil((double) filtered.size() / size);
        return new AuditEventPage(filtered.subList(start, end), page, size, filtered.size(), totalPages);
    }

    synchronized String exportEvents(String enterpriseId, AuditQuery query, Authentication authentication) {
        requireAdmin(enterpriseId, authentication);
        List<AuditEventView> filtered = events.values().stream()
                .filter(event -> enterpriseId.equals(event.enterpriseId()))
                .filter(event -> query == null || query.matches(event))
                .sorted(Comparator.comparing(AuditEventView::occurredAt).reversed()
                        .thenComparing(AuditEventView::eventId))
                .toList();
        StringBuilder csv = new StringBuilder("eventId,eventType,enterpriseId,requestId,traceId,invocationId,attemptId,initiator,actor,effectiveSubject,agentInstanceId,toolId,resource,action,decision,reasonCode,occurredAt,eventDigest\n");
        for (AuditEventView event : filtered) {
            csv.append(String.join(",", csvValue(event.eventId()), csvValue(event.eventType()),
                    csvValue(event.enterpriseId()), csvValue(event.requestId()), csvValue(event.traceId()),
                    csvValue(event.invocationId()), csvValue(event.attemptId()), csvValue(event.initiator()),
                    csvValue(event.actor()), csvValue(event.effectiveSubject()), csvValue(event.agentInstanceId()),
                    csvValue(event.toolId()), csvValue(event.resource()), csvValue(event.action()),
                    csvValue(event.decision()), csvValue(event.reasonCode()), csvValue(event.occurredAt()),
                    csvValue(event.eventDigest()))).append('\n');
        }
        return csv.toString();
    }

    synchronized AuditEventView event(String e,String id,Authentication a){requireAdmin(e,a);AuditEventView v=events.get(id);if(v==null||!e.equals(v.enterpriseId()))throw SummerException.withMessage(AuditLedgerErrorCode.CONFLICT, "Event not found");return v;}
    synchronized List<McpInvocationView> invocations(String e,AuditQuery q,Authentication a){requireAdmin(e,a);return invocations.values().stream().filter(v->e.equals(v.enterpriseId())).filter(v->q==null||q.matches(v)).toList();}
    synchronized McpInvocationView invocation(String e,String id,Authentication a){requireAdmin(e,a);McpInvocationView v=invocations.get(id);if(v==null||!e.equals(v.enterpriseId()))throw SummerException.withMessage(AuditLedgerErrorCode.CONFLICT, "Invocation not found");return v;}
    synchronized List<AuditEventView> anomalies(String e,Authentication a){return events(e,new AuditQuery(null,null,null,null,null,null,null,null,"AUDIT_ANOMALY",null,null),a);}
    private void rebuild(AuditEventView e){ List<AuditEventView> es=events.values().stream().filter(x->x.invocationId().equals(e.invocationId())).toList(); AuditEventView d=es.stream().filter(x->x.decision()!=null||x.detail().containsKey("decision")).findFirst().orElse(null); AuditEventView c=es.stream().filter(x->x.eventType().contains("COMPLETED")||x.eventType().contains("RECONCILED")).reduce((x,y)->y).orElse(null); Map<String,Object> detail=c==null?Map.of():c.detail(); String status=detail.get("resultStatus") instanceof String s?s:null; Map<String,Object> dd=d==null?Map.of():d.detail(); String actor=dd.get("actor") instanceof String s?s:(d==null?e.actor():d.actor()); String subject=dd.get("effectiveSubject") instanceof String s?s:(d==null?e.effectiveSubject():d.effectiveSubject()); String decision=dd.get("decision") instanceof String s?s:(d==null?e.decision():d.decision()); String tool=dd.get("toolId") instanceof String s?s:(d==null?e.toolId():d.toolId()); AuditEventView p=es.stream().filter(x->x.permitId()!=null||x.detail().containsKey("permitId")).findFirst().orElse(null); String permit=p==null?e.permitId():(p.permitId()!=null?p.permitId():String.valueOf(p.detail().get("permitId"))); String jti=p==null?e.permitJti():(p.permitJti()!=null?p.permitJti():String.valueOf(p.detail().get("permitJti"))); McpInvocationView v=new McpInvocationView(e.invocationId(),e.enterpriseId(),e.requestId(),e.traceId(),d==null?e.initiator():d.initiator(),actor,subject,d==null?e.agentInstanceId():d.agentInstanceId(),tool,d==null?e.resource():d.resource(),d==null?e.action():d.action(),decision,d==null?e.reasonCode():d.reasonCode(),d==null?e.policyVersion():d.policyVersion(),permit,jti,c==null?e.attemptId():c.attemptId(),status,detail.get("resultDigest") instanceof String s?s:null,detail.get("remoteRequestId") instanceof String s?s:null,detail.get("latencyMs") instanceof Number n?n.longValue():null,false,es);invocations.put(v.invocationId(),v);}
    private void anomaly(String id,String reason){String eventId="anomaly-"+id;if(!events.containsKey(eventId)){Map<String,Object> detail=Map.of("reason",reason);AuditEventView v=new AuditEventView(eventId,"AUDIT_ANOMALY",ENTERPRISE,"","",id,null,null,null,null,null,null,null,null,null,null,null,null,null,null,detail,Instant.now(),null,"anomaly");events.put(eventId,v);}}
    private void requireService(Authentication a){if(!(a instanceof JwtAuthenticationToken j)||!Boolean.TRUE.equals(j.getToken().getClaimAsBoolean("service_trust_verified"))||j.getAuthorities().stream().noneMatch(x->x.getAuthority().equals("SCOPE_internal.audit.write")))throw new AccessDeniedException("Audit service trust denied");}
    private boolean active(JwtAuthenticationToken j,String e){Object x=j.getToken().getClaims().get("enterprise_memberships");return x instanceof Collection<?> c&&c.stream().filter(Map.class::isInstance).map(z->(Map<?,?>)z).anyMatch(m->e.equals(String.valueOf(m.get("enterpriseId")))&&"ACTIVE".equals(String.valueOf(m.get("status"))));}
    private boolean cap(JwtAuthenticationToken j,String e,String c){Object x=j.getToken().getClaims().get("enterprise_capabilities");return x instanceof Map<?,?> m&&m.get(e) instanceof Collection<?> v&&v.stream().map(String::valueOf).anyMatch(c::equals);}
    private Map<String,Object> redact(Map<String,Object> d){
        if (d == null) return Map.of();
        Map<String,Object> output = new LinkedHashMap<>();
        d.forEach((key, value) -> {
            if (value != null && !sensitiveKey(key)) output.put(key, redactValue(value));
        });
        return Map.copyOf(output);
    }
    private Object redactValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String,Object> nested = new LinkedHashMap<>();
            map.forEach((key, item) -> {
                String name = String.valueOf(key);
                if (item != null && !sensitiveKey(name)) nested.put(name, redactValue(item));
            });
            return Map.copyOf(nested);
        }
        if (value instanceof Collection<?> collection) return collection.stream().map(this::redactValue).toList();
        return value;
    }
    private boolean sensitiveKey(String key) {
        String normalized = key.toLowerCase(Locale.ROOT).replace("_", "").replace("-", "");
        return normalized.contains("token") || normalized.contains("secret") || normalized.contains("password")
                || normalized.contains("privatekey") || normalized.contains("rawresult") || normalized.equals("authorization");
    }
    private static String csvValue(Object value) {
        if (value == null) return "";
        String text = String.valueOf(value);
        return '"' + text.replace("\"", "\"\"").replace("\r", " ").replace("\n", " ") + '"';
    }
    private void requireAdmin(String e,Authentication a){if(!(a instanceof JwtAuthenticationToken j)||blank(e)||!active(j,e)||!cap(j,e,"enterprise.audit.read")||j.getAuthorities().stream().noneMatch(x->x.getAuthority().equals("SCOPE_enterprise.audit.read")))throw new AccessDeniedException("Audit capability denied");}
    private static boolean blank(String s){return s==null||s.isBlank();}private static void requireKey(String k){if(blank(k))throw SummerException.withMessage(AuditLedgerErrorCode.INVALID, "Idempotency-Key is required");}
}
