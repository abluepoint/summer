package com.abluepoint.summer.sample.connection;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Non-production adapter used by Summer Sample. It models the protocol seam without
 * storing or returning a reusable OAuth token and without making network calls.
 */
@Component
public final class InMemoryConnectionServicePort implements ConnectionServicePort {
    private final Map<String, ResourceToken> tokens = new HashMap<>();
    private final Map<String, ConnectionSession> sessions = new HashMap<>();
    private final List<ExecutionStatusReceipt> receipts = new ArrayList<>();
    private final List<String> events = new ArrayList<>();
    private int sequence;

    @Override
    public synchronized ResourceToken acquireResourceToken(ResourceTokenRequest request) {
        require(request != null && nonBlank(request.enterpriseId()) && nonBlank(request.connectionServiceId()) && nonBlank(request.audience()),
                "enterpriseId, connection service and audience are required");
        String ref = "token-" + (++sequence);
        ResourceToken token = new ResourceToken(request.enterpriseId(), ref, request.audience(), Instant.now().plusSeconds(60), null);
        tokens.put(ref, token);
        events.add("token");
        return token;
    }

    @Override
    public synchronized ConnectionSession openSession(ConnectionSessionRequest request) {
        require(request != null && nonBlank(request.enterpriseId()) && nonBlank(request.tokenRef()), "enterpriseId and tokenRef are required");
        ResourceToken token = tokens.get(request.tokenRef());
        require(token != null, "resource token is unknown");
        require(token.enterpriseId().equals(request.enterpriseId()), "enterprise context mismatch");
        String ref = nonBlank(request.requestedSessionRef()) ? request.requestedSessionRef() : "session-" + (++sequence);
        ConnectionSession session = new ConnectionSession(request.enterpriseId(), ref, "ACTIVE", Instant.now().plusSeconds(300));
        sessions.put(ref, session);
        events.add("session");
        return session;
    }

    @Override
    public synchronized McpProviderResult invoke(McpProviderRequest request) {
        require(request != null && nonBlank(request.sessionRef()) && nonBlank(request.toolName()),
                "sessionRef and toolName are required");
        require(sessions.containsKey(request.sessionRef()), "session is unknown");
        events.add("invoke");
        long timeoutMs = request.timeout() == null ? 1000 : request.timeout().toMillis();
        if (timeoutMs < 10) {
            return new McpProviderResult("EXECUTION_UNKNOWN", Math.min(3, Math.max(0, request.maxRetries())) + 1,
                    null, request.remoteRequestRef(), timeoutMs);
        }
        return new McpProviderResult("SUCCEEDED", Math.min(3, Math.max(0, request.maxRetries())) + 1,
                "sha256:redacted", request.remoteRequestRef(), 1);
    }

    @Override
    public synchronized void publish(ExecutionStatusReceipt receipt) {
        require(receipt != null && nonBlank(receipt.invocationId()) && nonBlank(receipt.executionAttemptId()),
                "invocationId and executionAttemptId are required");
        for (ExecutionStatusReceipt existing : receipts) {
            if (Objects.equals(existing.invocationId(), receipt.invocationId())
                    && Objects.equals(existing.executionAttemptId(), receipt.executionAttemptId())) {
                if (!sameOutcome(existing, receipt)) {
                    throw new IllegalArgumentException("execution receipt conflicts with an existing receipt");
                }
                return;
            }
        }
        receipts.add(receipt);
        events.add("receipt");
    }

    public synchronized List<ExecutionStatusReceipt> publishedReceipts() {
        return List.copyOf(receipts);
    }

    public synchronized List<String> events() {
        return List.copyOf(events);
    }

    private static boolean nonBlank(String value) {
        return value != null && !value.isBlank();
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalArgumentException(message);
    }

    private static boolean sameOutcome(ExecutionStatusReceipt left, ExecutionStatusReceipt right) {
        return Objects.equals(left.sessionRef(), right.sessionRef())
                && Objects.equals(left.resultStatus(), right.resultStatus())
                && Objects.equals(left.resultDigest(), right.resultDigest())
                && Objects.equals(left.remoteRequestRef(), right.remoteRequestRef());
    }
}
