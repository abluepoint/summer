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

/** An explicit offline operator action; ordinary startup never contracts storage. */
@Component
@ConditionalOnProperty("summer.sample.authorization.contract.command")
public class ControlledAuthorizationContractCommand implements ApplicationRunner {
  private static final String PREFIX="summer.sample.authorization.contract.";
  private final ControlledAuthorizationContract contract;
  private final ControlledDomainModeRecovery backup;
  private final ObjectMapper json;
  private final Environment environment;

  public ControlledAuthorizationContractCommand(ControlledAuthorizationContract contract,
      ControlledDomainModeRecovery backup,ObjectMapper json,Environment environment) {
    this.contract=contract;this.backup=backup;this.json=json;this.environment=environment;
  }

  @Override
  public void run(ApplicationArguments arguments) throws Exception {
    Path output=Path.of(environment.getRequiredProperty(PREFIX+"report-file"));
    if(Files.exists(output,LinkOption.NOFOLLOW_LINKS))throw new IllegalArgumentException("New contract report path required");
    Map<String,Object> report;
    switch(environment.getRequiredProperty(PREFIX+"command")) {
      case "inspect" -> report=backup.inspectAllManaged();
      case "contract-approved" -> {
        byte[] approval=protectedFile("approval-file");
        String hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(approval));
        if(!hash.equals(environment.getRequiredProperty(PREFIX+"approval-sha256")))
          throw new IllegalArgumentException("Exact protected storage-contract approval required");
        report=contract.execute(json.readValue(approval,ControlledAuthorizationContract.Approval.class),hash,
            protectedFile("restore-receipt-file"),protectedFile("management-receipt-file"));
      }
      default -> throw new IllegalArgumentException("Unknown authorization storage-contract command");
    }
    Files.writeString(output,json.writeValueAsString(report),StandardOpenOption.CREATE_NEW);
  }

  private byte[] protectedFile(String property) throws Exception {
    Path file=Path.of(environment.getRequiredProperty(PREFIX+property));
    if(!Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS)||Files.size(file)>262144)
      throw new IllegalArgumentException("Protected bounded contract input required");
    var permissions=Files.getPosixFilePermissions(file);
    if(permissions.contains(PosixFilePermission.GROUP_WRITE)||permissions.contains(PosixFilePermission.OTHERS_WRITE))
      throw new IllegalArgumentException("Contract input cannot be writable by other users");
    return Files.readAllBytes(file);
  }
}
