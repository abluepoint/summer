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

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:resource-package-upgrade;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
@Import(InterfaceResourcePackageUpgradeHttpTest.HistoricalCatalog.class)
class InterfaceResourcePackageUpgradeHttpTest {
    @Autowired MockMvc mvc;

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
            String json = mapper.writeValueAsString(new InterfaceResourceSnapshot(List.of(historical), List.of()));
            repository.saveAndFlush(new InterfaceResourceRevisionEntity("pre-package-migration", json, 1));
            return new InterfaceResourceHistory(store, policy);
        }
    }
}
