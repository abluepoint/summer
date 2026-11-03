package com.abluepoint.summer.sample.task;

import com.abluepoint.summer.mvc.error.DefaultErrorStatusResolver;
import com.abluepoint.summer.mvc.error.ErrorStatusResolver;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "spring.quartz.job-store-type=memory")
@AutoConfigureMockMvc
@Import(TaskErrorStatusHttpTest.TransportPolicy.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class TaskErrorStatusHttpTest {
    @Autowired MockMvc mvc;

    @Test
    void applicationStatusPolicyAppliesToARealDuplicateWithoutCreatingAnotherTask() throws Exception {
        String body = """
                {"name":"Unified status policy","taskType":"system.noop","cronExpression":"0 0 0 1 1 ? 2099"}
                """;
        mvc.perform(post("/api/v1/enterprises/ent-acme/tasks").with(admin())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/v1/enterprises/ent-acme/tasks").with(admin())
                        .header("X-Request-Id", "unified-task-conflict")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().is(422))
                .andExpect(header().string("X-Request-Id", "unified-task-conflict"))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("SUMMER_TASK_CONFLICT"))
                .andExpect(jsonPath("$.error.message").value("Task name already exists"))
                .andExpect(jsonPath("$.error.violations").isEmpty())
                .andExpect(jsonPath("$.requestId").value("unified-task-conflict"));
        mvc.perform(get("/api/v1/enterprises/ent-acme/tasks").with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(1));
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor admin() {
        return jwt().jwt(token -> token.subject("task-admin")
                        .claim("enterprise_memberships", List.of(Map.of("enterpriseId", "ent-acme", "status", "ACTIVE")))
                        .claim("enterprise_capabilities", Map.of("ent-acme", List.of("enterprise.tasks.write", "enterprise.tasks.read"))))
                .authorities(new SimpleGrantedAuthority("SCOPE_enterprise.tasks.write"),
                        new SimpleGrantedAuthority("SCOPE_enterprise.tasks.read"));
    }

    @TestConfiguration
    static class TransportPolicy {
        @Bean @Primary ErrorStatusResolver taskTransportPolicy() {
            var defaults = new DefaultErrorStatusResolver();
            return code -> "SUMMER_TASK_CONFLICT".equals(code.code()) ? HttpStatus.valueOf(422) : defaults.resolve(code);
        }
    }
}
