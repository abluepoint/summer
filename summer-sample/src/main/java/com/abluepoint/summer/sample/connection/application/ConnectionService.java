package com.abluepoint.summer.sample.connection.application;
import com.abluepoint.summer.sample.connection.domain.ConnectionValidationException;
import com.abluepoint.summer.sample.connection.domain.ConnectionIdempotencyConflictException;
import com.abluepoint.summer.sample.connection.domain.ConnectionPreconditionException;
import com.abluepoint.summer.sample.connection.domain.ConnectionConflictException;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;

import java.time.Instant;
import com.abluepoint.summer.sample.connection.domain.ConnectionCatalogRules;
import java.util.*;

@Service
public final class ConnectionService {
    private static final String ENTERPRISE = "ent-acme";
    private final Map<String, ServiceRecord> services = new LinkedHashMap<>();
    private final Map<String, BindingRecord> bindings = new LinkedHashMap<>();
    private final Map<String, Receipt> receipts = new HashMap<>();

    public synchronized List<ConnectionServiceView> listServices(Authentication auth) {
        requirePlatform(auth, "platform.connection-services.read");
        return services.values().stream().map(this::serviceView).toList();
    }
    public synchronized ConnectionServiceView createService(ConnectionServiceCreateCommand request, String key, Authentication auth) {
        requirePlatform(auth, "platform.connection-services.write");
        requireKey(key); String fp = fingerprint(request); Receipt prior = receipts.get("platform:" + key); if (prior != null) return replay(prior, fp);
        if (request == null || blank(request.serviceCode()) || blank(request.name()) || blank(request.permitAudience())) throw new ConnectionValidationException("serviceCode, name and permitAudience are required");
        if (services.values().stream().anyMatch(s -> s.serviceCode.equals(request.serviceCode().trim()))) throw new ConnectionConflictException("serviceCode already exists");
        ServiceRecord s = new ServiceRecord("cs-" + UUID.randomUUID().toString().substring(0, 8), request.serviceCode().trim(), request.name().trim(), request.permitAudience().trim(), request.protocolVersion() == null ? "v1" : request.protocolVersion(), "ACTIVE", 1, Instant.now());
        services.put(s.id, s); ConnectionServiceView out = serviceView(s); receipts.put("platform:" + key, new Receipt(fp, out)); return out;
    }
    public synchronized ConnectionServiceView setServiceStatus(String serviceId, String status, String ifMatch, String key, Authentication auth) {
        requirePlatform(auth, "platform.connection-services.write"); requireKey(key);
        String fp = fingerprint(serviceId, status, ifMatch); Receipt prior = receipts.get("platform:" + key); if (prior != null) return replay(prior, fp);
        ServiceRecord service = services.get(serviceId); if (service == null) throw new ConnectionConflictException("Connection service not found");
        if (ifMatch == null || !ifMatch.equals("\"" + service.version + "\"")) throw new ConnectionPreconditionException("The resource has changed");
        ConnectionCatalogRules.requireStatus(status);
        service.status = status; service.version++; ConnectionServiceView out = serviceView(service); receipts.put("platform:" + key, new Receipt(fp, out)); return out;
    }
    public synchronized McpConnectionView syncCatalog(String serviceId, String externalConnectionId, CatalogSyncCommand request, Authentication auth) {
        requireService(serviceId, auth); if (request == null || blank(request.enterpriseId()) || !ENTERPRISE.equals(request.enterpriseId())) throw new ConnectionValidationException("enterpriseId is required");
        if (request.connectionRevision() < 1 || blank(request.catalogDigest()) || request.mcpServer() == null) throw new ConnectionValidationException("catalog revision, digest and server are required");
        String key = serviceId + ":" + externalConnectionId; BindingRecord current = bindings.get(key);
        if (current != null) {
            if (ConnectionCatalogRules.isReplay(request.connectionRevision(), request.catalogDigest(), current.connectionRevision, current.catalogDigest)) return bindingView(current);
        }
        String serverId = current == null ? "mcp-server-" + UUID.randomUUID().toString().substring(0, 8) : current.server.serverId;
        String clientId = request.mcpClient() == null ? null : request.mcpClient().clientId();
        if (blank(clientId)) throw new ConnectionValidationException("mcpClient.clientId is required");
        McpClientProjectionState client = new McpClientProjectionState(request.mcpClient());
        McpServerProjectionState server = new McpServerProjectionState(serverId, request.mcpServer());
        List<McpToolProjectionState> tools = new ArrayList<>();
        if (request.tools() != null) for (McpToolProjection tool : request.tools()) {
            if (tool == null || blank(tool.toolName()) || blank(tool.schemaDigest())) throw new ConnectionValidationException("toolName and schemaDigest are required");
            tools.add(new McpToolProjectionState("mcp-tool-" + UUID.randomUUID().toString().substring(0, 8), tool));
        }
        BindingRecord next = new BindingRecord(request.enterpriseId(), serviceId, externalConnectionId, request.connectionRevision(), request.catalogDigest(), client, server, tools, "ACTIVE", current == null ? 1 : current.version + 1, Instant.now());
        bindings.put(key, next); return bindingView(next);
    }
    public synchronized List<McpConnectionView> listBindings(String enterpriseId, Authentication auth) { requireEnterprise(enterpriseId, auth); return bindings.values().stream().filter(b -> enterpriseId.equals(b.enterpriseId)).map(this::bindingView).toList(); }
    public synchronized McpConnectionView binding(String enterpriseId, String externalConnectionId, Authentication auth) { requireEnterprise(enterpriseId, auth); return bindings.values().stream().filter(b -> enterpriseId.equals(b.enterpriseId) && externalConnectionId.equals(b.externalConnectionId)).findFirst().map(this::bindingView).orElseThrow(() -> new ConnectionConflictException("Connection not found")); }
    public synchronized McpConnectionHealthView health(String enterpriseId, String externalConnectionId, Authentication auth) { McpConnectionView view = binding(enterpriseId, externalConnectionId, auth); return new McpConnectionHealthView(view.enterpriseId(), view.connectionServiceId(), view.externalConnectionId(), view.status(), view.version(), view.lastSyncedAt()); }
    public synchronized List<McpToolView> tools(String enterpriseId, String externalConnectionId, Authentication auth) { return binding(enterpriseId, externalConnectionId, auth).tools(); }

    public synchronized McpConnectionView createBinding(String enterpriseId, McpConnectionCreateCommand request, String key, Authentication auth) {
        requireEnterpriseWrite(enterpriseId, auth);
        requireKey(key);
        if (request == null || blank(request.connectionServiceId()) || blank(request.externalConnectionId())) {
            throw new ConnectionValidationException("connectionServiceId and externalConnectionId are required");
        }
        String receiptKey = "enterprise:" + enterpriseId + ":" + key;
        String fingerprint = fingerprint(request);
        Receipt prior = receipts.get(receiptKey);
        if (prior != null) return replay(prior, fingerprint);
        ServiceRecord service = services.get(request.connectionServiceId());
        if (service == null || !"ACTIVE".equals(service.status)) throw new ConnectionConflictException("Connection service is not active");
        String bindingKey = service.id + ":" + request.externalConnectionId().trim();
        if (bindings.values().stream().anyMatch(existing -> enterpriseId.equals(existing.enterpriseId)
                && request.externalConnectionId().trim().equals(existing.externalConnectionId))) {
            throw new ConnectionConflictException("Connection already exists");
        }
        String externalId = request.externalConnectionId().trim();
        McpClientProjectionState client = new McpClientProjectionState(new McpClientProjection(null, "pending-" + externalId, null, null, List.of(), "", "MANUAL"));
        McpServerProjectionState server = new McpServerProjectionState("mcp-server-" + UUID.randomUUID().toString().substring(0, 8), new McpServerProjection(externalId, null, service.permitAudience, service.permitAudience, null));
        BindingRecord record = new BindingRecord(enterpriseId, service.id, externalId, 1, "pending", client, server, new ArrayList<>(), "PENDING", 1, Instant.now());
        bindings.put(bindingKey, record);
        McpConnectionView result = bindingView(record);
        receipts.put(receiptKey, new Receipt(fingerprint, result));
        return result;
    }

    private void requirePlatform(Authentication auth, String capability) { if (!(auth instanceof JwtAuthenticationToken jwt) || !platformClaim(jwt, capability) || jwt.getAuthorities().stream().noneMatch(a -> ("SCOPE_" + capability).equals(a.getAuthority()))) throw new AccessDeniedException("Platform connection-service capability denied"); }
    private boolean platformClaim(JwtAuthenticationToken jwt, String capability) { Object value = jwt.getToken().getClaims().get("platform_capabilities"); return value instanceof Collection<?> c && c.stream().map(String::valueOf).anyMatch(capability::equals); }
    private void requireEnterprise(String enterpriseId, Authentication auth) { if (!ENTERPRISE.equals(enterpriseId) || !(auth instanceof JwtAuthenticationToken jwt) || !activeMembership(jwt, enterpriseId) || !enterpriseCapability(jwt, enterpriseId, "enterprise.mcp-connections.read") || jwt.getAuthorities().stream().noneMatch(a -> "SCOPE_enterprise.mcp-connections.read".equals(a.getAuthority()))) throw new AccessDeniedException("MCP connection capability denied"); }
    private void requireEnterpriseWrite(String enterpriseId, Authentication auth) { if (!ENTERPRISE.equals(enterpriseId) || !(auth instanceof JwtAuthenticationToken jwt) || !activeMembership(jwt, enterpriseId) || !enterpriseCapability(jwt, enterpriseId, "enterprise.connection.write") || jwt.getAuthorities().stream().noneMatch(a -> "SCOPE_enterprise.connection.write".equals(a.getAuthority()))) throw new AccessDeniedException("MCP connection write capability denied"); }
    private boolean activeMembership(JwtAuthenticationToken jwt, String enterpriseId) { Object value = jwt.getToken().getClaims().get("enterprise_memberships"); return value instanceof Collection<?> c && c.stream().filter(Map.class::isInstance).map(v -> (Map<?, ?>) v).anyMatch(m -> enterpriseId.equals(String.valueOf(m.getOrDefault("enterpriseId", m.get("enterprise_id")))) && "ACTIVE".equalsIgnoreCase(String.valueOf(m.getOrDefault("status", m.get("membership_status"))))); }
    private boolean enterpriseCapability(JwtAuthenticationToken jwt, String enterpriseId, String capability) { Object value = jwt.getToken().getClaims().get("enterprise_capabilities"); if (!(value instanceof Map<?, ?> map)) return false; Object granted = map.get(enterpriseId); return granted instanceof Collection<?> c && c.stream().map(String::valueOf).anyMatch(capability::equals); }
    private ServiceRecord requireService(String serviceId, Authentication auth) { if (!(auth instanceof JwtAuthenticationToken jwt) || !jwt.getToken().getClaimAsBoolean("service_trust_verified") || !serviceId.equals(jwt.getToken().getClaimAsString("connection_service_id")) || jwt.getAuthorities().stream().noneMatch(a -> "SCOPE_internal.connection.catalog.write".equals(a.getAuthority()))) throw new AccessDeniedException("Connection service trust denied"); ServiceRecord service = services.get(serviceId); if (service == null || !"ACTIVE".equals(service.status)) throw new AccessDeniedException("Connection service is not active"); service.lastSeenAt = Instant.now(); return service; }
    private static void requireKey(String key) { if (blank(key)) throw new ConnectionValidationException("Idempotency-Key is required"); }
    private static boolean blank(String value) { return value == null || value.isBlank(); }
    private static String fingerprint(Object... value) { return Integer.toHexString(Objects.hashCode(Arrays.deepToString(value))); }
    @SuppressWarnings("unchecked") private <T> T replay(Receipt receipt, String fp) { if (!receipt.fingerprint.equals(fp)) throw new ConnectionIdempotencyConflictException("Idempotency key reused"); return (T) receipt.result; }
    private ConnectionServiceView serviceView(ServiceRecord s) { return new ConnectionServiceView(s.id, s.serviceCode, s.name, s.permitAudience, s.protocolVersion, s.status, s.version, s.lastSeenAt); }
    private McpConnectionView bindingView(BindingRecord b) { return new McpConnectionView(b.enterpriseId, b.serviceId, b.externalConnectionId, b.connectionRevision, b.catalogDigest, b.client.view(), b.server.view(), b.tools.stream().map(McpToolProjectionState::view).toList(), b.status, b.version, b.lastSyncedAt); }
    private record Receipt(String fingerprint, Object result) {}
    private static final class ServiceRecord { final String id, serviceCode, name, permitAudience, protocolVersion; String status; long version; Instant lastSeenAt; ServiceRecord(String i,String c,String n,String a,String p,String s,long v,Instant l){id=i;serviceCode=c;name=n;permitAudience=a;protocolVersion=p;status=s;version=v;lastSeenAt=l;} }
    private static final class BindingRecord { final String enterpriseId, serviceId, externalConnectionId, catalogDigest, status; final long connectionRevision; final McpClientProjectionState client; final McpServerProjectionState server; final List<McpToolProjectionState> tools; final long version; final Instant lastSyncedAt; BindingRecord(String e,String s,String x,long r,String d,McpClientProjectionState c,McpServerProjectionState v,List<McpToolProjectionState> t,String st,long ver,Instant l){enterpriseId=e;serviceId=s;externalConnectionId=x;connectionRevision=r;catalogDigest=d;client=c;server=v;tools=t;status=st;version=ver;lastSyncedAt=l;} }
    private static final class McpClientProjectionState { final String externalRegistrationRef, clientId, clientName, clientVersion, authMethod, registrationType; final List<String> redirectUris; McpClientProjectionState(McpClientProjection r){externalRegistrationRef=r.externalRegistrationRef();clientId=r.clientId();clientName=r.clientName();clientVersion=r.clientVersion();redirectUris=r.redirectUris()==null?List.of():List.copyOf(r.redirectUris());authMethod=r.authMethod();registrationType=r.registrationType();} McpClientView view(){return new McpClientView(externalRegistrationRef,clientId,clientName,clientVersion,redirectUris,authMethod,registrationType);} }
    private static final class McpServerProjectionState { final String serverId,name,resourceUri,audience,authorizationServerIssuer,metadataVersion; McpServerProjectionState(String id,McpServerProjection r){serverId=id;name=r.name();resourceUri=r.resourceUri();audience=r.audience();authorizationServerIssuer=r.authorizationServerIssuer();metadataVersion=r.metadataVersion();} McpServerView view(){return new McpServerView(serverId,name,resourceUri,audience,authorizationServerIssuer,metadataVersion);} }
    private static final class McpToolProjectionState { final String id, externalToolId, toolName, schemaDigest, riskLevel, requiredScope, requiredCapabilityCode, status; final long catalogRevision; McpToolProjectionState(String i,McpToolProjection r){id=i;externalToolId=r.externalToolId();toolName=r.toolName();schemaDigest=r.schemaDigest();catalogRevision=r.catalogRevision();riskLevel=r.riskLevel();requiredScope=r.requiredScope();requiredCapabilityCode=r.requiredCapabilityCode();status="ACTIVE";} McpToolView view(){return new McpToolView(id,externalToolId,toolName,schemaDigest,catalogRevision,riskLevel,requiredScope,requiredCapabilityCode,status);} }
}
