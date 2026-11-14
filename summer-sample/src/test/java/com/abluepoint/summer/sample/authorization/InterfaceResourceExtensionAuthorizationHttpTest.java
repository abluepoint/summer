package com.abluepoint.summer.sample.authorization;

import com.example.catalog.CatalogExtensionController;
import com.example.catalog.ManagedInterfaceExtensionFixture;
import com.abluepoint.summer.sample.authorization.infrastructure.permission.ManagedInterfaceResources;
import com.abluepoint.summer.security.authorization.ResourceAuthorization.Domain;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
@Import({CurrentManagedAuthorizationTestFixture.class, CatalogExtensionController.class, ManagedInterfaceExtensionFixture.class})
@DirtiesContext
class InterfaceResourceExtensionAuthorizationHttpTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate db;
    @Autowired ManagedInterfaceResources resources;

    @Test void unknownMvcOwnerIsClosedAndExplicitRuntimeAdapterUsesCurrentGrant() throws Exception {
        resources.scan();
        var actor = jwt().jwt(token -> token.subject("summer-user").claim("scope", "platform.interface-resources.read")
            .claim("platform_capabilities", List.of("GET:/api/catalog-extension")));
        assertThat(db.queryForObject("select count(*) from summer_interface_resource where normalized_path='/api/catalog-extension' and configurable=true", Integer.class)).isZero();
        for (String mode : List.of("one", "two"))
            mvc.perform(get("/api/catalog-extension").param("mode", mode).with(actor)).andExpect(status().isForbidden());
        String path = "/api/v1/platform/interface-extension";
        String resource = db.queryForObject("select resource_id from summer_interface_resource where normalized_path=? and http_method='GET' and configurable=true and active=true", String.class, path);
        String permission = db.queryForObject("select permission_id from summer_permission where domain_key='PLATFORM:platform' and code='management-read'", String.class);
        mvc.perform(get(path).with(actor)).andExpect(status().isForbidden());
        db.update("insert into summer_permission_resource values ('PLATFORM:platform',?,'INTERFACE',?)", permission, resource);
        mvc.perform(get(path).with(actor)).andExpect(status().isOk());
        db.update("delete from summer_permission_resource where domain_key='PLATFORM:platform' and permission_id=? and resource_id=?", permission, resource);
        db.update("update summer_authorization_domain set revision=revision+1 where domain_key='PLATFORM:platform'");
        mvc.perform(get(path).with(actor)).andExpect(status().isForbidden());
    }
}
