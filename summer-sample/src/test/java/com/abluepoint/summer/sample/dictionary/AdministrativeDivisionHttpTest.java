package com.abluepoint.summer.sample.dictionary;

import com.abluepoint.summer.sample.authorization.BusinessManagedAuthorizationTestFixture;
import com.abluepoint.summer.sample.authorization.infrastructure.permission.ManagedInterfaceResources;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext
class AdministrativeDivisionHttpTest {
    @Autowired MockMvc http;
    @Autowired JdbcTemplate db;
    @Autowired PlatformTransactionManager transactions;
    @Autowired ManagedInterfaceResources resources;

    @Test
    @Sql("/db/migration/mysql/V193__administrative_division.sql")
    void treeEditsRequireCurrentRevisionAndPreserveParent() throws Exception {
        String url = "/api/v1/platform/administrative-divisions";
        var actor = jwt().jwt(j -> j.subject("division-editor").claim("scope", "platform.dictionary.read platform.dictionary.write"))
                .authorities(new SimpleGrantedAuthority("SCOPE_platform.dictionary.read"), new SimpleGrantedAuthority("SCOPE_platform.dictionary.write"));
        try (var fixture = new BusinessManagedAuthorizationTestFixture(db, transactions, resources)) {
            fixture.interfaces("PLATFORM:platform", "division-editor", Set.of("administrative-divisions"));
            http.perform(post(url).with(actor).contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {"code":"BAD","name":"　","level":5}
                            """)).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.violations[*].field",
                            org.hamcrest.Matchers.containsInAnyOrder("code", "name", "level")));
            http.perform(post(url).with(actor).param("name", "来自查询").contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {"code":"110000","level":1}
                            """)).andExpect(status().isBadRequest());
            http.perform(post(url).with(actor).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"code\":\"110000\",\"level\":1,\"name\":\"　北京市　\",\"parentId\":null}")).andExpect(status().isOk());
            String parent = db.queryForObject("SELECT id FROM summer_administrative_division WHERE code='110000'", String.class);
            http.perform(post(url).with(actor).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"code\":\"110101\",\"level\":3,\"name\":\"东城区\",\"parentId\":\"" + parent + "\"}")).andExpect(status().isOk());
            http.perform(get(url).with(actor)).andExpect(jsonPath("$.data.length()").value(1))
                    .andExpect(jsonPath("$.data[0].name").value("北京市"))
                    .andExpect(jsonPath("$.data[0].hasChildren").value(true));
            http.perform(get(url).param("parentId", parent).with(actor)).andExpect(jsonPath("$.data[0].name").value("东城区"))
                    .andExpect(jsonPath("$.data[0].updatedAt").isString());
            String child = db.queryForObject("SELECT id FROM summer_administrative_division WHERE code='110101'", String.class);
            var before = db.queryForObject("SELECT updated_at FROM summer_administrative_division WHERE id=?", java.sql.Timestamp.class, child);
            http.perform(put(url+"/"+child).with(actor).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"revision\":1,\"name\":\"更新后的名称\"}")).andExpect(status().isOk());
            assertTrue(db.queryForObject("SELECT updated_at FROM summer_administrative_division WHERE id=?", java.sql.Timestamp.class, child).after(before));
            http.perform(delete(url+"/"+child).param("revision","1").with(actor)).andExpect(status().isConflict());
            http.perform(delete(url+"/"+parent).param("revision","1").with(actor)).andExpect(status().isConflict());
            http.perform(delete(url+"/"+child).param("revision","2").with(actor)).andExpect(status().isOk());
            fixture.revokeInterfaces("PLATFORM:platform","division-editor");
            http.perform(get(url).with(actor)).andExpect(status().isForbidden());
        }
    }
}
