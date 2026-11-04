package com.abluepoint.summer.sample.authorization.api.permission;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
class PermissionManagementHttpTest {
    @Autowired MockMvc mvc;

    @Test
    void sessionWithoutPersistentMembershipCannotManagePermissions() throws Exception {
        mvc.perform(
                        get("/api/v1/enterprises/ent-acme/permissions")
                                .with(user("no-persisted-grants")))
                .andExpect(status().isForbidden());
    }
}
