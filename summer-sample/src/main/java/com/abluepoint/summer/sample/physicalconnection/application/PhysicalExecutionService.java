package com.abluepoint.summer.sample.physicalconnection.application;
import com.abluepoint.summer.sample.connection.domain.McpProviderResult;
import com.abluepoint.summer.sample.connection.domain.ResourceToken;
import com.abluepoint.summer.sample.connection.domain.ExecutionStatusReceipt;
import com.abluepoint.summer.sample.connection.domain.ConnectionSession;
import com.abluepoint.summer.sample.connection.domain.ConnectionServicePort;
import com.abluepoint.summer.sample.connection.domain.ConnectionSessionRequest;
import com.abluepoint.summer.sample.connection.domain.McpProviderRequest;
import com.abluepoint.summer.sample.connection.domain.ResourceTokenRequest;
import com.abluepoint.summer.sample.mcpauthorization.application.McpAuthorizationService;
import com.abluepoint.summer.sample.mcpauthorization.domain.PublicPermitSnapshot;
import com.abluepoint.summer.sample.physicalconnection.domain.PhysicalExecutionConflictException;
import com.abluepoint.summer.sample.physicalconnection.domain.PhysicalExecutionValidationException;

import com.abluepoint.summer.sample.auditledger.application.AuditLedgerService;
import com.abluepoint.summer.sample.auditledger.domain.ExecutionEvidence;
import com.abluepoint.summer.sample.connection.domain.ConnectionServicePort;
import com.abluepoint.summer.sample.connection.domain.ConnectionSession;
import com.abluepoint.summer.sample.connection.domain.ConnectionSessionRequest;
import com.abluepoint.summer.sample.connection.domain.ExecutionStatusReceipt;
import com.abluepoint.summer.sample.connection.domain.McpProviderResult;
import com.abluepoint.summer.sample.connection.domain.ResourceToken;
import com.abluepoint.summer.sample.connection.domain.ResourceTokenRequest;
import com.abluepoint.summer.sample.mcpauthorization.application.McpAuthorizationService;
import com.abluepoint.summer.sample.mcpauthorization.domain.PublicPermitSnapshot;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import com.abluepoint.summer.sample.physicalconnection.domain.PhysicalExecutionRules;
import java.util.Map;
import java.time.Duration;

@Service
public final class PhysicalExecutionService {
    private final McpAuthorizationService auth;
    private final AuditLedgerService audit;
    private final ConnectionServicePort connectionPort;
    private final Map<String, PhysicalExecutionView> attempts = new HashMap<>();

    public PhysicalExecutionService(McpAuthorizationService auth, AuditLedgerService audit, ConnectionServicePort connectionPort) {
        this.auth = auth;
        this.audit = audit;
        this.connectionPort = connectionPort;
    }

    public synchronized PhysicalExecutionView invoke(String connectionId, String tool, String serviceId,
                                               PhysicalInvokeCommand request, Authentication authentication) {
        if (!(authentication instanceof JwtAuthenticationToken jwt)
                || !Boolean.TRUE.equals(jwt.getToken().getClaimAsBoolean("service_trust_verified"))
                || !serviceId.equals(jwt.getToken().getClaimAsString("connection_service_id"))) {
            throw new AccessDeniedException("Connection service trust denied");
        }
        if (request == null || blank(request.permitId()) || blank(request.executionAttemptId())
                || blank(request.schemaDigest()) || blank(request.argumentsDigest()) || blank(request.sessionRef())) {
            throw new PhysicalExecutionValidationException("permit, attempt, digests and session are required");
        }
        String attemptKey = request.permitId() + ":" + request.executionAttemptId();
        PhysicalExecutionView prior = attempts.get(attemptKey);
        if (prior != null) {
            if (!prior.sessionRef().equals(request.sessionRef())) {
                throw new PhysicalExecutionConflictException("Execution session mismatch");
            }
            return prior;
        }
        PublicPermitSnapshot permit = auth.snapshot(request.permitId());
        PhysicalExecutionRules.requireBinding(serviceId, connectionId, tool, request.schemaDigest(), request.argumentsDigest(),
                permit.connectionServiceId(), permit.externalConnectionId(), permit.toolName(), permit.schemaDigest(), permit.argumentsDigest());
        auth.claimForExecution(request.permitId(), request.executionAttemptId(), authentication);
        int retries = PhysicalExecutionRules.retries(request.maxRetries());
        long timeout = request.timeoutMs() == null ? 1000 : request.timeoutMs();
        ResourceToken token = connectionPort.acquireResourceToken(new ResourceTokenRequest(
                permit.enterpriseId(), serviceId, connectionId, permit.audience(), permit.audience(), permit.permitId()));
        ConnectionSession session = connectionPort.openSession(new ConnectionSessionRequest(
                permit.enterpriseId(), serviceId, connectionId, permit.audience(), token.tokenRef(), request.sessionRef()));
        McpProviderResult provider = connectionPort.invoke(new com.abluepoint.summer.sample.connection.domain.McpProviderRequest(
                "remote-" + request.executionAttemptId(), session.sessionRef(), tool,
                request.schemaDigest(), request.argumentsDigest(), Duration.ofMillis(timeout), retries));
        String status = provider.status();
        PhysicalExecutionView output = new PhysicalExecutionView(permit.invocationId(), permit.permitId(),
                request.executionAttemptId(), tool, status, request.sessionRef(), retries + 1,
                provider.resultDigest(), provider.remoteRequestRef());
        attempts.put(attemptKey, output);
        audit.recordExecution(new ExecutionEvidence(permit.enterpriseId(), permit.invocationId(), request.executionAttemptId(),
                jwt.getToken().getClaimAsString("initiator_principal_id"), permit.actorPrincipalId(),
                permit.effectiveSubjectPrincipalId(), permit.actorInstanceId(), null, permit.connectionRevision(),
                permit.permitId(), null, permit.toolId(), permit.externalConnectionId(), "mcp.invoke", "ALLOW", "AUTHORIZED",
                status, output.resultDigest() == null ? "sha256:unknown" : output.resultDigest(), output.remoteRequestRef(), null,
                output.sessionRef()), authentication);
        connectionPort.publish(new ExecutionStatusReceipt(permit.invocationId(), request.executionAttemptId(),
                output.sessionRef(), status, output.resultDigest(), output.remoteRequestRef(), provider.latencyMs(),
                java.time.Instant.now()));
        return output;
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
