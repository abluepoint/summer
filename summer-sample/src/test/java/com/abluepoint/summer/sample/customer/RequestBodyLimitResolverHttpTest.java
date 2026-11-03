package com.abluepoint.summer.sample.customer;

import com.abluepoint.summer.common.message.MessageResolver;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "summer.mvc.request-body.max-bytes=8")
@AutoConfigureMockMvc
@Import(RequestBodyLimitResolverHttpTest.Messages.class)
class RequestBodyLimitResolverHttpTest {
    @Autowired MockMvc mvc;

    @Test void filterFailureUsesTheApplicationsUnifiedMessageResolver() throws Exception {
        mvc.perform(post("/api/customers").contentType("application/json")
                        .header("X-Request-Id", "limit-resolver").content("123456789"))
                .andExpect(status().is(413))
                .andExpect(jsonPath("$.error.code").value("SUMMER_REQUEST_BODY_TOO_LARGE"))
                .andExpect(jsonPath("$.error.message").value("Application resolved body limit"))
                .andExpect(jsonPath("$.requestId").value("limit-resolver"))
                .andExpect(header().string("X-Request-Id", "limit-resolver"));
    }

    @Test void filterMessagesUseEachRequestLanguageWithoutLeakingItToTheNextRequest() throws Exception {
        for (String[] example : new String[][]{{"fr", "Limite du corps"}, {"en", "Application resolved body limit"}}) {
            mvc.perform(post("/api/customers").contentType("application/json")
                            .header("Accept-Language", example[0]).content("123456789"))
                    .andExpect(status().is(413))
                    .andExpect(jsonPath("$.error.message").value(example[1]));
        }
    }

    @TestConfiguration
    static class Messages {
        @Bean @Primary MessageResolver limitMessages() {
            return (code, arguments, locale) -> locale.getLanguage().equals("fr")
                    ? "Limite du corps" : "Application resolved body limit";
        }
    }
}
