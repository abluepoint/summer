package com.abluepoint.summer.sample.authorization.api.permission;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/** Exercises the explicit operator input and then the public management API. */
@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:managed-installation14;MODE=MySQL;DB_CLOSE_DELAY=-1",
    "summer.sample.authorization.managed-bootstrap-domains=PLATFORM:platform"
})
@org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
class ManagedDomainInstallationHttpTest {
  @Autowired MockMvc mvc;
  @Autowired com.abluepoint.summer.sample.authorization.infrastructure.permission.ManagedAuthorizationBootstrap installation;
  static Path report;
  static Path approvedFile;
  static String approvedHash;

  @DynamicPropertySource
  static void approvedInstallation(DynamicPropertyRegistry properties) throws Exception {
    Path directory = Files.createTempDirectory("managed-installation-");
    Path approval = directory.resolve("approved.json");
    report = directory.resolve("report.json");
    Files.writeString(approval, """
        {"format":"summer-managed-installation/1","purpose":"FRESH_INSTALL",
         "operationId":"%s","domainKind":"PLATFORM","domainId":"platform","actor":"summer-user"}
        """.formatted(UUID.randomUUID()));
    Files.setPosixFilePermissions(approval, PosixFilePermissions.fromString("rw-------"));
    approvedFile = approval;
    properties.add("summer.sample.authorization.installation.approval-file", approval::toString);
    String hash = java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
        .digest(Files.readAllBytes(approval)));
    approvedHash = hash;
    properties.add("summer.sample.authorization.installation.approval-sha256", () -> hash);
    properties.add("summer.sample.authorization.installation.report-file", report::toString);
  }

  @TestConfiguration
  static class Schema {
    @Bean
    @Order(-100)
    ApplicationRunner schema(JdbcTemplate db) {
      return args -> {
        for (String name : List.of("V60__permission_management.sql",
            "V70__role_permission_and_resource_associations.sql", "V80__permission_resource_controls.sql",
            "V81__managed_interface_lifecycle.sql", "V92__support_full_length_menu_resource_ids.sql",
            "V160__authorization_migration_ledger.sql", "V170__authorization_data_migration_proof.sql",
            "V180__authorization_controlled_installation.sql")) {
          String sql = new String(new ClassPathResource("db/migration/mysql/" + name)
              .getContentAsByteArray(), StandardCharsets.UTF_8);
          if (name.startsWith("V60")) sql = sql.substring(0, sql.indexOf("ALTER TABLE"));
          for (String statement : sql.replaceAll("(?m)--[^\\n]*", "").split(";"))
            if (!statement.isBlank()) db.execute(statement.replace("CREATE TABLE summer_permission_role",
                "CREATE TABLE IF NOT EXISTS summer_permission_role"));
        }
        db.update("insert into summer_menu_publication(publication_id,published,manifest_version) values ('global',false,0) on duplicate key update publication_id=publication_id");
      };
    }
  }

  @Test
  void explicitApprovedInstallationCreatesUsableManagementWithoutStartupRepair() throws Exception {
    assertTrue(Files.isRegularFile(report));
    var result = new tools.jackson.databind.ObjectMapper().readTree(Files.readString(report));
    assertEquals("INSTALLED", result.path("status").asText());
    assertEquals("PLATFORM:platform", result.path("domainKey").asText());
    mvc.perform(get("/api/v1/platform/roles").with(user("summer-user")))
        .andExpect(status().isOk());
    var binding = mvc.perform(get("/api/v1/platform/users/summer-user/roles").with(user("summer-user")))
        .andExpect(status().isOk()).andReturn();
    mvc.perform(put("/api/v1/platform/users/summer-user/roles").with(user("summer-user")).with(csrf())
            .header("If-Match", binding.getResponse().getHeader("ETag"))
            .header("Idempotency-Key", UUID.randomUUID()).contentType("application/json")
            .content("{\"roleIds\":[]}"))
        .andExpect(status().isOk());
    mvc.perform(get("/api/v1/platform/roles").with(user("summer-user")))
        .andExpect(status().isForbidden());
    Path replay = report.resolveSibling("replay.json");
    var configuration = new org.springframework.mock.env.MockEnvironment()
        .withProperty("summer.sample.authorization.installation.approval-file", approvedFile.toString())
        .withProperty("summer.sample.authorization.installation.approval-sha256", approvedHash)
        .withProperty("summer.sample.authorization.installation.report-file", replay.toString());
    new com.abluepoint.summer.sample.authorization.infrastructure.permission.ManagedInstallationCommand(
        installation, new tools.jackson.databind.ObjectMapper(), configuration).run(null);
    assertEquals("ALREADY_INSTALLED", new tools.jackson.databind.ObjectMapper()
        .readTree(Files.readString(replay)).path("status").asText());
    mvc.perform(get("/api/v1/platform/roles").with(user("summer-user")))
        .andExpect(status().isForbidden());
  }
}
