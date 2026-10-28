package com.abluepoint.summer.sample.connection.api;
import java.time.Instant;
import java.util.List;
import com.abluepoint.summer.sample.connection.application.McpConnectionCreateCommand;
public record McpConnectionCreateRequest(String connectionServiceId, String externalConnectionId) {
 public McpConnectionCreateCommand toCommand() { return new McpConnectionCreateCommand(connectionServiceId,externalConnectionId); }
}
