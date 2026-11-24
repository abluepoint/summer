package com.abluepoint.summer.sample.resource;

import com.abluepoint.summer.sample.authorization.CurrentManagedAuthorizationTestFixture;
import com.abluepoint.summer.security.authorization.ResourceAuthorization.Domain;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Import(CurrentManagedAuthorizationTestFixture.class)
class InterfaceServiceDirectoryHttpTest {
    private static final String ROOT = "/api/v1/platform/interface-resources";
    @Autowired MockMvc mvc;
    @org.springframework.test.context.bean.override.mockito.MockitoBean
    com.abluepoint.summer.sample.dictionary.InterfaceServiceNames dictionary;
    @Autowired CurrentManagedAuthorizationTestFixture grants;

    @Test
    @Sql(scripts = "/db/migration/mysql/V188__interface_service_catalog.sql")
    @Sql(statements = {
        "alter table summer_interface_service_catalog drop primary key",
        "alter table summer_interface_service_catalog add service_code varchar(80) primary key"
    })
    @Sql(scripts = "/db/migration/mysql/V192__interface_service_last_sync.sql")
    void createsAndReusesNamesAndFiltersTheWholeDirectoryBeforePaging() throws Exception {
        var a = new com.abluepoint.summer.sample.dictionary.InterfaceServiceNames.Option("order","A",true);
        var b = new com.abluepoint.summer.sample.dictionary.InterfaceServiceNames.Option("stock","B",true);
        var disabled = new com.abluepoint.summer.sample.dictionary.InterfaceServiceNames.Option("off","Off",false);
        org.mockito.Mockito.when(dictionary.published()).thenReturn(java.util.List.of(a,b,disabled));
        String actor = "platform-admin";
        var auth = jwt().jwt(jwt -> jwt.subject(actor).claim("scope", "platform.capabilities.write platform.interface-resources.read"));
        grants.grantHandlers(Domain.platform(), actor, (handler, path, method) -> path.equals(ROOT) && method.equals("GET"));
        mvc.perform(get(ROOT).param("directory","true").with(auth)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].revision").isNumber())
                .andExpect(jsonPath("$.data.items[0].lastSyncedAt").doesNotExist())
                .andExpect(jsonPath("$.data.serviceNames").isEmpty()).andExpect(jsonPath("$.data.items[0].normalizedPath").isString());
        var save = post(ROOT + "/refresh").with(auth).contentType("application/json");
        mvc.perform(save.content("{\"serviceName\":\"A\"}")).andExpect(status().isForbidden());
        grants.grantHandlers(Domain.platform(), actor, (handler, path, method) -> path.equals(ROOT + "/refresh") && method.equals("POST"));
        mvc.perform(save.content("{\"serviceName\":\"  \"}")).andExpect(status().isBadRequest());
        for (String invalid : java.util.List.of("arbitrary","off")) {
            mvc.perform(save.content("{\"serviceCode\":\"" + invalid + "\"}")).andExpect(status().isBadRequest());
        }
        var mapper = new tools.jackson.databind.ObjectMapper();
        var syncs = new java.util.HashMap<String, tools.jackson.databind.JsonNode>();
        for (var option : java.util.List.of(a,b,a)) {
            var response = mvc.perform(save.content("{\"serviceCode\":\"" + option.value() + "\"}")).andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.serviceName").value(option.label())).andReturn();
            var result = mapper.readTree(response.getResponse().getContentAsString()).path("data");
            var previous = syncs.put(option.value(), result);
            if (previous != null) org.junit.jupiter.api.Assertions.assertTrue(
                    java.time.Instant.parse(result.path("lastSyncedAt").asString()).isAfter(
                    java.time.Instant.parse(previous.path("lastSyncedAt").asString())));
        }
        org.mockito.Mockito.when(dictionary.published()).thenReturn(java.util.List.of(
            new com.abluepoint.summer.sample.dictionary.InterfaceServiceNames.Option("order","A renamed",true),b));
        mvc.perform(get(ROOT).param("directory","true").param("serviceCode","order").param("path",ROOT)
                .param("policy","MANAGED_ROLE").param("page","1").param("size","1").with(auth))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.serviceNames.length()").value(2))
                .andExpect(jsonPath("$.data.items[0].revision").value(syncs.get("order").path("revision").asInt()))
                .andExpect(jsonPath("$.data.items[0].lastSyncedAt").value(syncs.get("order").path("lastSyncedAt").asString()))
                .andExpect(jsonPath("$.data.total").value(org.hamcrest.Matchers.greaterThan(1)))
                .andExpect(jsonPath("$.data.items.length()").value(1)).andExpect(jsonPath("$.data.items[0].serviceName").value("A renamed"))
                .andExpect(jsonPath("$.data.items[0].configurationPolicy").value("MANAGED_ROLE"))
                .andExpect(jsonPath("$.data.items[0].normalizedPath").value(org.hamcrest.Matchers.containsString(ROOT)));
        org.mockito.Mockito.when(dictionary.published()).thenReturn(java.util.List.of(a));
        mvc.perform(get(ROOT).param("directory","true").param("serviceCode","stock").with(auth))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.services[1].label").value("B"))
                .andExpect(jsonPath("$.data.services[1].enabled").value(false))
                .andExpect(jsonPath("$.data.items[0].serviceName").value("B"))
                .andExpect(jsonPath("$.data.items[0].lastSyncedAt").value(syncs.get("stock").path("lastSyncedAt").asString()));
        mvc.perform(save.content("{\"serviceCode\":\"stock\"}")).andExpect(status().isBadRequest());
        mvc.perform(get(ROOT).param("directory","true").param("serviceName","B").param("path","/absent-route-xyz").with(auth))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(0));
    }
}
