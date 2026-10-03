package com.abluepoint.summer.sample.connection;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ConnectionServicePortTest {
    @Test
    void acquiresResourceScopedToken_opensSession_invokesProvider_andPublishesReceipt() {
        InMemoryConnectionServicePort port = new InMemoryConnectionServicePort();
        ResourceToken token = port.acquireResourceToken(new ResourceTokenRequest(
                "ent-acme", "cs-1", "ext-1", "https://mcp.example.test", "urn:mcp:crm", "permit-1"));
        ConnectionSession session = port.openSession(new ConnectionSessionRequest(
                "ent-acme", "cs-1", "ext-1", "https://mcp.example.test", token.tokenRef(), "session-1"));
        McpProviderResult result = port.invoke(new McpProviderRequest(
                "remote-1", session.sessionRef(), "search", "sha256:schema", "sha256:args", Duration.ofSeconds(1), 0));
        port.publish(new ExecutionStatusReceipt(
                "inv-1", "attempt-1", session.sessionRef(), result.status(), result.resultDigest(),
                result.remoteRequestRef(), result.latencyMs(), Instant.now()));

        assertEquals("urn:mcp:crm", token.audience());
        assertFalse(token.tokenValue().isPresent(), "raw OAuth token must stay inside adapter");
        assertEquals("SUCCEEDED", result.status());
        assertEquals(1, port.publishedReceipts().size());
        assertEquals("session-1", port.publishedReceipts().getFirst().sessionRef());
        assertEquals(List.of("token", "session", "invoke", "receipt"), port.events());
    }

    @Test
    void timeout_is_reported_as_unknown_and_retry_count_is_bounded() {
        InMemoryConnectionServicePort port = new InMemoryConnectionServicePort();
        ResourceToken token = port.acquireResourceToken(new ResourceTokenRequest(
                "ent-acme", "cs-1", "ext-1", "https://mcp.example.test", "urn:mcp:crm", "permit-2"));
        ConnectionSession session = port.openSession(new ConnectionSessionRequest(
                "ent-acme", "cs-1", "ext-1", "https://mcp.example.test", token.tokenRef(), "session-timeout"));
        McpProviderResult result = port.invoke(new McpProviderRequest(
                "remote-timeout", session.sessionRef(), "search", "schema", "args", Duration.ofMillis(1), 2));

        assertEquals("EXECUTION_UNKNOWN", result.status());
        assertEquals(3, result.attempts());
        assertTrue(port.events().contains("invoke"));
    }

    @Test
    void session_cannot_cross_enterprise_boundary_of_resource_token() {
        InMemoryConnectionServicePort port = new InMemoryConnectionServicePort();
        ResourceToken token = port.acquireResourceToken(new ResourceTokenRequest(
                "ent-acme", "cs-1", "ext-1", "https://mcp.example.test", "urn:mcp:crm", "permit-3"));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () ->
                port.openSession(new ConnectionSessionRequest(
                        "ent-other", "cs-1", "ext-1", "https://mcp.example.test", token.tokenRef(), "session-cross-tenant")));

        assertEquals("enterprise context mismatch", error.getMessage());
        assertTrue(port.events().contains("token"));
        assertFalse(port.events().contains("session"));
    }

    @Test
    void receipt_replay_with_different_result_is_rejected_instead_of_silently_dropped() {
        InMemoryConnectionServicePort port = new InMemoryConnectionServicePort();
        ExecutionStatusReceipt first = new ExecutionStatusReceipt(
                "inv-conflict", "attempt-1", "session-1", "SUCCEEDED", "sha256:result-1",
                "remote-1", 12, Instant.now());
        port.publish(first);

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () ->
                port.publish(new ExecutionStatusReceipt(
                        "inv-conflict", "attempt-1", "session-1", "FAILED", "sha256:result-2",
                        "remote-1", 12, Instant.now())));

        assertEquals("execution receipt conflicts with an existing receipt", error.getMessage());
        assertEquals(1, port.publishedReceipts().size());
    }
}
