package com.abluepoint.summer.sample.authorization.infrastructure.permission;

import com.abluepoint.summer.security.authorization.ResourceAuthorization.Domain;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermission;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/** Explicit local operator action. No normal startup or HTTP request selects this command. */
@Component
@ConditionalOnProperty(name = "summer.sample.authorization.installation.approval-file")
public class ManagedInstallationCommand {
  private static final String PREFIX = "summer.sample.authorization.installation.";
  private final ManagedAuthorizationBootstrap installation;
  private final ObjectMapper json;
  private final Environment environment;

  public ManagedInstallationCommand(ManagedAuthorizationBootstrap installation,
      ObjectMapper json, Environment environment) {
    this.installation = installation;
    this.json = json;
    this.environment = environment;
  }

  @org.springframework.context.event.EventListener(org.springframework.boot.context.event.ApplicationReadyEvent.class)
  @Order(1)
  public void initialize() throws Exception {
    run(null);
  }

  public void run(ApplicationArguments arguments) throws Exception {
    Path approval = Path.of(environment.getRequiredProperty(PREFIX + "approval-file"));
    Path report = Path.of(environment.getRequiredProperty(PREFIX + "report-file"));
    if (Files.exists(report) || !Files.isRegularFile(approval, LinkOption.NOFOLLOW_LINKS)
        || Files.size(approval) > 8192)
      throw new IllegalArgumentException("A new report and a bounded regular approval file are required");
    var permissions = Files.getPosixFilePermissions(approval, LinkOption.NOFOLLOW_LINKS);
    if (permissions.contains(PosixFilePermission.GROUP_WRITE)
        || permissions.contains(PosixFilePermission.OTHERS_WRITE))
      throw new IllegalArgumentException("Approval must not be writable by other principals");
    byte[] bytes = Files.readAllBytes(approval);
    String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    if (!hash.equals(environment.getRequiredProperty(PREFIX + "approval-sha256")))
      throw new IllegalArgumentException("Approved installation input changed");
    var input = json.readTree(bytes);
    Set<String> fields = Set.of("format", "purpose", "operationId", "domainKind", "domainId", "actor");
    if (!input.isObject() || !input.propertyNames().equals(fields)
        || !"summer-managed-installation/1".equals(input.path("format").asText())
        || !"FRESH_INSTALL".equals(input.path("purpose").asText())
        || !"PLATFORM".equals(input.path("domainKind").asText())
        || !"platform".equals(input.path("domainId").asText()))
      throw new IllegalArgumentException("Explicit first PLATFORM installation approval required");
    String operation = input.path("operationId").asText();
    String requestId = "installation-" + java.util.UUID.randomUUID();
    boolean created = installation.provision(Domain.platform(), input.path("actor").asText(),
        operation, hash, requestId);
    Files.writeString(report, json.writeValueAsString(Map.of("status", created ? "INSTALLED" : "ALREADY_INSTALLED",
        "domainKey", Domain.platform().key(), "operationId", operation, "approvalSha256", hash,
        "requestId", requestId)), StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
  }
}
