package com.abluepoint.summer.sample.authorization.api.permission;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

/** Generic configured public patterns cannot replace the precise public credential chain. */
@SpringBootTest(
    properties = {
      "summer.security.resource-server.protected-paths[0]=/api/**",
      "summer.security.resource-server.protected-paths[1]=/public/**"
    })
@org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
@Import(
    com.abluepoint.summer.sample.authorization.ExplicitLegacyBusinessDomains.AcmeAndPlatform.class)
@DirtiesContext
class PublicConfigurationChainOverlapHttpTest {
  @Autowired MockMvc mvc;

  @Test
  void configuredOverlapPreservesLegacyAnonymousReadsAndRejectsInvalidBearer() throws Exception {
    for (String route :
        List.of(
            "/public/enterprises/ent-acme/dictionaries/overlap-final09",
            "/public/enterprises/ent-acme/system-parameters/overlap-final09")) {
      mvc.perform(get(route)).andExpect(status().isNotFound());
      mvc.perform(get(route).header("Authorization", "Bearer invalid-public09"))
          .andExpect(status().isUnauthorized());
    }
  }
}
