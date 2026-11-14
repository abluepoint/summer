package com.abluepoint.summer.sample.authorization.api.permission;

import static org.junit.jupiter.api.Assertions.assertThrows;

import com.abluepoint.summer.sample.authorization.infrastructure.permission.ManagedAuthorizationBootstrap;
import com.abluepoint.summer.security.authorization.ResourceAuthorization.Domain;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:managed-old-platform14;MODE=MySQL;DB_CLOSE_DELAY=-1",
    "summer.sample.authorization.managed-bootstrap-domains="
})
@Import(ManagedDomainInstallationHttpTest.Schema.class)
class ManagedPlatformHistoryInstallationTest {
  @Autowired ManagedAuthorizationBootstrap installation;
  @Autowired JdbcTemplate db;

  @Test
  void platformCapabilityWithNullOwnerIsHistoryEvenWithoutModeOrMarker() {
    // The original createPlatformCapability persists precisely this NULL-owner PLATFORM shape.
    db.update("insert into summer_authorization_capability(capability_id,enterprise_id,scope,code,resource,action,risk_level,status,version,created_at,updated_at) values (?,null,'PLATFORM',?,'enterprise','read','HIGH','ACTIVE',1,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",
        "old-platform14", "platform.old14.read");
    assertThrows(AccessDeniedException.class, () -> installation.provision(Domain.platform(),
        "summer-user", UUID.randomUUID().toString(), "a".repeat(64), "old-platform-must-not-reinitialize"));
  }
}
