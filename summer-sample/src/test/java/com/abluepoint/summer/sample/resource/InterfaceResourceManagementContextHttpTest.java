package com.abluepoint.summer.sample.resource;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"management.server.port=0", "management.endpoints.web.base-path=/management-only", "management.endpoints.web.exposure.include=health"})
@org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
class InterfaceResourceManagementContextHttpTest {
    @Autowired MockMvc mvc;
    @Test void includesTheActualSeparateManagementContext() throws Exception {
        var response = mvc.perform(get("/api/v1/platform/interface-resources").with(jwt()
                        .jwt(token -> token.subject("catalog-reader").claim("platform_capabilities", List.of("platform.interface-resources.read")))
                        .authorities(new SimpleGrantedAuthority("SCOPE_platform.interface-resources.read"))))
                .andExpect(status().isOk()).andReturn();
        List<Map<String,Object>> rows = JsonPath.read(response.getResponse().getContentAsString(), "$.data.items");
        var health = rows.stream().filter(row -> "/management-only/health".equals(row.get("normalizedPath"))).toList();
        assertThat(health).isNotEmpty().allSatisfy(row -> assertThat(row).containsEntry("contextId", "management").containsEntry("resourceType", "INFRASTRUCTURE"));
    }
}
