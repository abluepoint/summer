package com.abluepoint.summer.sample.authorization.infrastructure.migration;

import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermission;
import java.security.MessageDigest;
import java.util.*;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/** Separate from LEGACY migration and ordinary bootstrap. Explicit local operator action only. */
@Component
@ConditionalOnProperty("summer.sample.authorization.recovery.command")
public class ControlledDomainModeRecoveryCommand implements ApplicationRunner {
  private static final String PREFIX="summer.sample.authorization.recovery.";
  private final ControlledDomainModeRecovery recovery;
  private final ObjectMapper json;
  private final Environment environment;

  public ControlledDomainModeRecoveryCommand(ControlledDomainModeRecovery recovery,
      ObjectMapper json,Environment environment) {
    this.recovery=recovery; this.json=json; this.environment=environment;
  }

  @Override
  public void run(ApplicationArguments arguments) throws Exception {
    Path output=Path.of(environment.getRequiredProperty(PREFIX+"report-file"));
    if(Files.exists(output,LinkOption.NOFOLLOW_LINKS))throw new IllegalArgumentException("New recovery report path required");
    Map<String,Object> report;
    switch(environment.getRequiredProperty(PREFIX+"command")) {
      case "inspect" -> report=recovery.inspect(environment.getRequiredProperty(PREFIX+"domain-key"));
      case "inspect-all-managed" -> report=recovery.inspectAllManaged();
      case "recover-approved" -> {
        byte[] approval=protectedFile("approval-file",262144);
        String hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(approval));
        if(!hash.equals(environment.getRequiredProperty(PREFIX+"approval-sha256")))
          throw new IllegalArgumentException("Exact protected recovery approval required");
        report=recovery.execute(json.readValue(approval,ControlledDomainModeRecovery.Approval.class),hash,
            protectedFile("restore-receipt-file",262144));
      }
      default -> throw new IllegalArgumentException("Unknown managed mode recovery command");
    }
    Files.writeString(output,json.writeValueAsString(report),StandardOpenOption.CREATE_NEW);
  }

  private byte[] protectedFile(String property,long maximum) throws Exception {
    Path file=Path.of(environment.getRequiredProperty(PREFIX+property));
    if(!Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS)||Files.size(file)>maximum)
      throw new IllegalArgumentException("Protected bounded recovery input required");
    var permissions=Files.getPosixFilePermissions(file);
    if(permissions.contains(PosixFilePermission.GROUP_WRITE)||permissions.contains(PosixFilePermission.OTHERS_WRITE))
      throw new IllegalArgumentException("Recovery input cannot be writable by other users");
    return Files.readAllBytes(file);
  }
}
