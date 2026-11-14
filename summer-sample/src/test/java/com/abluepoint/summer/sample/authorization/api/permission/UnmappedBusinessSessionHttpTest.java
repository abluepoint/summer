package com.abluepoint.summer.sample.authorization.api.permission;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.UUID;
import com.jayway.jsonpath.JsonPath;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Runs the installed Security filter chain and final MVC guard, without a domain adapter. */
@SpringBootTest(properties = {
    "summer.security.resource-server.protected-paths[0]=/api/**",
    "summer.security.resource-server.protected-paths[1]=/internal/**",
    "summer.sample.authorization.managed-bootstrap-domains=ENTERPRISE:ent-acme"
})
@org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
@Import({UnmappedBusinessSessionHttpTest.Fixture.class, RolePermissionHttpTest.Schema.class})
class UnmappedBusinessSessionHttpTest {
  @Autowired MockMvc mvc;
  @Autowired UnmappedController controller;

  @BeforeEach void reset() { controller.calls.set(0); }

  @ParameterizedTest
  @ValueSource(strings = {"/api/unmapped-business-fixture", "/internal/unmapped-business-fixture"})
  void anAuthenticatedHttpSessionCannotInvokeAnUnmappedBusinessHandler(String path) throws Exception {
    mvc.perform(get(path).with(user("unassigned-user")))
        .andExpect(status().isForbidden());
    assertThat(controller.calls).hasValue(0);
  }

  @ParameterizedTest
  @ValueSource(strings = {"/api/unmapped-business-fixture", "/internal/unmapped-business-fixture"})
  void anAuthenticatedBearerCannotInvokeTheSameUnmappedBusinessHandler(String path) throws Exception {
    mvc.perform(get(path).with(jwt()))
        .andExpect(status().isForbidden());
    assertThat(controller.calls).hasValue(0);
  }

  @Test void theExplicitOwnSessionProjectionRemainsAvailable() throws Exception {
    mvc.perform(get("/api/v1/session").with(user("unassigned-user")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.principal.subject").value("unassigned-user"));
  }

  @Test void anExistingApplicantSessionCanSupplementAndWithdrawWithoutChangingIdentityRules() throws Exception {
    String email = "session-applicant@example.test";
    String route = "/api/v1/enterprises/ent-acme/join-applications";
    var scope = new SimpleGrantedAuthority("SCOPE_enterprise.join-application.submit");
    var submitted = mvc.perform(post(route).with(jwt().jwt(t -> t.subject(email)).authorities(scope))
            .with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .header("Idempotency-Key", UUID.randomUUID().toString())
            .content("{\"applicantEmail\":\"" + email + "\",\"materials\":[]}"))
        .andExpect(status().isCreated()).andReturn();
    String id = JsonPath.read(submitted.getResponse().getContentAsString(), "$.data.applicationId");
    String etag = submitted.getResponse().getHeader("ETag");
    String item = route + "/" + id;
    // The same scope cannot make a session satisfy the service's JWT email identity rule.
    mvc.perform(post(route).with(user(email).authorities(scope)).with(csrf())
            .contentType(MediaType.APPLICATION_JSON).header("Idempotency-Key", UUID.randomUUID().toString())
            .content("{\"applicantEmail\":\"" + email + "\"}"))
        .andExpect(status().isForbidden());
    mvc.perform(patch(item).with(user(email)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .header("If-Match", etag).header("Idempotency-Key", UUID.randomUUID().toString())
            .content("{\"materials\":[\"must not save\"]}"))
        .andExpect(status().isForbidden());
    mvc.perform(patch(item).with(user("different-applicant").authorities(scope)).with(csrf())
            .contentType(MediaType.APPLICATION_JSON).header("If-Match", etag)
            .header("Idempotency-Key", UUID.randomUUID().toString()).content("{\"materials\":[]}"))
        .andExpect(status().isForbidden());
    var supplemented = mvc.perform(patch(item).with(user(email).authorities(scope)).with(csrf())
            .contentType(MediaType.APPLICATION_JSON).header("If-Match", etag)
            .header("Idempotency-Key", UUID.randomUUID().toString())
            .content("{\"materials\":[\"session supplement\"]}"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.data.materials[0]").value("session supplement"))
        .andReturn();
    mvc.perform(post(item + ":withdraw").with(user(email).authorities(scope)).with(csrf())
            .header("If-Match", supplemented.getResponse().getHeader("ETag"))
            .header("Idempotency-Key", UUID.randomUUID().toString()))
        .andExpect(status().isOk()).andExpect(jsonPath("$.data.state").value("WITHDRAWN"));
  }

  @TestConfiguration(proxyBeanMethods = false)
  static class Fixture {
    @Bean UnmappedController unmappedController() { return new UnmappedController(); }
  }

  @RestController
  @TestComponent
  static class UnmappedController {
    final AtomicInteger calls = new AtomicInteger();
    @GetMapping({"/api/unmapped-business-fixture", "/internal/unmapped-business-fixture"}) String read() {
      calls.incrementAndGet();
      return "must never reach an unregistered business handler";
    }
  }
}
