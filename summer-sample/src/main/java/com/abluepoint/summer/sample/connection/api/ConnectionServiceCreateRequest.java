package com.abluepoint.summer.sample.connection.api;
import java.time.Instant;
import java.util.List;
import com.abluepoint.summer.sample.connection.application.ConnectionServiceCreateCommand;
public record ConnectionServiceCreateRequest(String serviceCode, String name, String permitAudience, String protocolVersion) {
 public ConnectionServiceCreateCommand toCommand() { return new ConnectionServiceCreateCommand(serviceCode,name,permitAudience,protocolVersion); }
}
