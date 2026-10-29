package com.abluepoint.summer.sample.resource;

import com.abluepoint.summer.sample.resource.application.InterfaceResourceHistory;
import com.abluepoint.summer.sample.resource.domain.InterfaceResourceBindingPolicy;
import com.abluepoint.summer.sample.resource.domain.InterfaceResourceDescriptor;
import com.abluepoint.summer.sample.resource.domain.InterfaceResourceRevisions;
import com.abluepoint.summer.sample.resource.domain.InterfaceResourceSnapshot;
import com.abluepoint.summer.sample.resource.domain.ResourceType;
import com.abluepoint.summer.sample.resource.domain.SensitiveLevel;
import com.abluepoint.summer.sample.resource.infrastructure.persistence.InterfaceResourceRevisionEntity;
import com.abluepoint.summer.sample.resource.infrastructure.persistence.InterfaceResourceRevisionRepository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import com.abluepoint.summer.sample.identity.domain.AuthAccountStore;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint;
import org.springframework.test.annotation.DirtiesContext;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:resource-package-upgrade;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "summer.security.storage.keys=mysql"
})
@org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Import(InterfaceResourcePackageUpgradeHttpTest.HistoricalCatalog.class)
class InterfaceResourcePackageUpgradeHttpTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired AuthAccountStore accounts;
    @Autowired PasswordEncoder encoder;

    @DynamicPropertySource
    static void isolatedKeyMaterial(DynamicPropertyRegistry registry) {
        String generated = UUID.randomUUID().toString();
        registry.add("summer.sample.security.master-key", () -> generated);
    }

    @Test
    void retainsUserCreationAndDefaultRoleAcrossTheNestedRequestSplit() throws Exception {
        String username = "upgrade-create-" + UUID.randomUUID();
        mvc.perform(post("/api/users").with(jwt().authorities(
                        new SimpleGrantedAuthority("SCOPE_enterprise.members.write")))
                        .contentType("application/json").content(mapper.writeValueAsString(Map.of(
                                "username", username, "password", UUID.randomUUID().toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value(username))
                .andExpect(jsonPath("$.data.role").value("USER"))
                .andExpect(jsonPath("$.data.active").value(true));
        assertThat(accounts.find(username)).get().extracting(account -> account.role()).isEqualTo("USER");
        for (String role : List.of("", "USER", "ADMIN")) {
            mvc.perform(post("/api/users").with(jwt().authorities(
                            new SimpleGrantedAuthority("SCOPE_enterprise.members.write")))
                            .contentType("application/json").content(mapper.writeValueAsString(Map.of(
                                    "username", "upgrade-role-" + UUID.randomUUID(), "password", UUID.randomUUID().toString(), "role", role))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.role").value(role.isBlank() ? "USER" : role));
        }
        for (Map<String, String> invalid : List.of(
                Map.of("username", "upgrade-invalid-" + UUID.randomUUID(), "password", "x".repeat(3)),
                Map.of("username", "upgrade-invalid-" + UUID.randomUUID(), "password", UUID.randomUUID().toString(), "role", "OWNER"))) {
            mvc.perform(post("/api/users").with(jwt().authorities(
                            new SimpleGrantedAuthority("SCOPE_enterprise.members.write")))
                            .contentType("application/json").content(mapper.writeValueAsString(invalid)))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    void retainsUserActivationAndPersistsItsChangeAcrossTheNestedRequestSplit() throws Exception {
        String username = "upgrade-active-" + UUID.randomUUID();
        accounts.create(username, encoder.encode(UUID.randomUUID().toString()), "USER");
        mvc.perform(patch("/api/users/{username}", username).with(jwt().authorities(
                        new SimpleGrantedAuthority("SCOPE_enterprise.members.write")))
                        .contentType("application/json").content("{\"active\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value(username))
                .andExpect(jsonPath("$.data.active").value(false));
        assertThat(accounts.find(username)).get().extracting(account -> account.active()).isEqualTo(false);
    }

    @Test
    void retainsKeyRotationAndItsVersionFenceAcrossTheNestedRequestSplit() throws Exception {
        var metadata = mvc.perform(get("/api/security/keys").with(jwt().authorities(
                        new SimpleGrantedAuthority("SCOPE_security.key.read"))))
                .andExpect(status().isOk()).andReturn();
        String etag = metadata.getResponse().getHeader("ETag");
        int version = Integer.parseInt(etag.replace("\"", ""));
        mvc.perform(post("/api/security/keys:rotate").with(jwt().authorities(
                        new SimpleGrantedAuthority("SCOPE_security.key.rotate")))
                        .header("If-Match", etag).header("Idempotency-Key", UUID.randomUUID())
                        .contentType("application/json").content("{\"retentionDays\":30}"))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"" + (version + 1) + "\""))
                .andExpect(jsonPath("$.data.activeKid").isString())
                .andExpect(jsonPath("$.data.retentionDays").value(30));
    }

    @Test
    void retainsObservedCustomerPermissionWhenOnlyItsJavaPackagesChange() throws Exception {
        mvc.perform(get("/api/customers").with(jwt().authorities(
                        new SimpleGrantedAuthority("SCOPE_customer.read"))))
                .andExpect(status().isOk());
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class HistoricalCatalog {
        @Bean
        @Primary
        InterfaceResourceHistory upgradeHistory(InterfaceResourceRevisionRepository repository, ObjectMapper mapper, InterfaceResourceRevisions store, InterfaceResourceBindingPolicy policy) {
            var historical = InterfaceResourceDescriptor.mvc("GET", "/api/customers",
                    "com.abluepoint.summer.sample.customer.CustomerController#list[class com.abluepoint.summer.mvc.validate.FieldValues]",
                    "customer", ResourceType.BUSINESS, SensitiveLevel.NORMAL, null, true);
            var create = InterfaceResourceDescriptor.mvc("POST", "/api/users",
                    "com.abluepoint.summer.sample.identity.AuthUserController#create[class com.abluepoint.summer.sample.identity.AuthUserController$CreateUserRequest]",
                    "identity", ResourceType.BUSINESS, SensitiveLevel.SENSITIVE, null, true);
            var active = InterfaceResourceDescriptor.mvc("PATCH", "/api/users/{username}",
                    "com.abluepoint.summer.sample.identity.AuthUserController#active[class java.lang.String, class com.abluepoint.summer.sample.identity.AuthUserController$ActiveUserRequest]",
                    "identity", ResourceType.BUSINESS, SensitiveLevel.SENSITIVE, null, true);
            var rotate = InterfaceResourceDescriptor.mvc("POST", "/api/security/keys:rotate",
                    "com.abluepoint.summer.sample.key.KeyCapabilityController#rotate[class com.abluepoint.summer.sample.key.KeyCapabilityController$RotationRequest, class java.lang.String, class java.lang.String, interface org.springframework.security.core.Authentication]",
                    "key", ResourceType.BUSINESS, SensitiveLevel.CRITICAL, null, true);
            String json = mapper.writeValueAsString(new InterfaceResourceSnapshot(List.of(historical, create, active, rotate), List.of()));
            repository.saveAndFlush(new InterfaceResourceRevisionEntity("pre-package-migration", json, 1));
            return new InterfaceResourceHistory(store, policy);
        }
    }
}
