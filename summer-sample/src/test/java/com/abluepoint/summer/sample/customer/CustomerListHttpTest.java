package com.abluepoint.summer.sample.customer;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
class CustomerListHttpTest {

    private static final String CLIENT_ID = "summer-client";
    private static final String CLIENT_SECRET = "summer-client-demo-only";

    @Autowired
    MockMvc mockMvc;

    @Test
    void sortsByEmailAndUsesUsernameAsStableTail() throws Exception {
        String writeToken = accessToken("customer.write");
        create(writeToken, "query-z", "z-query@summer.local", "Query Z");
        create(writeToken, "query-a", "a-query@summer.local", "Query A");
        create(writeToken, "query-tail-z", "tail-z-query@summer.local", "Same Tail");
        create(writeToken, "query-tail-a", "tail-a-query@summer.local", "Same Tail");

        String readToken = accessToken("customer.read");
        MvcResult result = mockMvc.perform(get("/api/customers")
                        .header("Authorization", "Bearer " + readToken)
                        .queryParam("sort", "email")
                        .queryParam("direction", "asc")
                        .queryParam("projection", "summary")
                        .queryParam("size", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.projection").value("summary"))
                .andExpect(jsonPath("$.data.items[0].active").doesNotExist())
                .andReturn();

        List<String> usernames = JsonPath.read(result.getResponse().getContentAsString(), "$.data.items[*].username");
        assertThat(usernames.indexOf("query-a")).isLessThan(usernames.indexOf("query-z"));

        MvcResult stableTail = mockMvc.perform(get("/api/customers")
                        .header("Authorization", "Bearer " + readToken)
                        .queryParam("sort", "displayName")
                        .queryParam("direction", "asc")
                        .queryParam("size", "100"))
                .andExpect(status().isOk())
                .andReturn();
        List<String> tailUsernames = JsonPath.read(stableTail.getResponse().getContentAsString(), "$.data.items[*].username");
        assertThat(tailUsernames.indexOf("query-tail-a")).isLessThan(tailUsernames.indexOf("query-tail-z"));
    }

    @Test
    void appliesActiveFilterAndReturnsEmptyPagesWithDefaultsAndBounds() throws Exception {
        String writeToken = accessToken("customer.write");
        create(writeToken, "query-inactive", "inactive-query@summer.local", "Inactive Query");
        mockMvc.perform(patch("/api/customers/{username}", "query-inactive")
                        .header("Authorization", "Bearer " + writeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":false}"))
                .andExpect(status().isOk());

        String readToken = accessToken("customer.read");
        mockMvc.perform(get("/api/customers")
                        .header("Authorization", "Bearer " + readToken)
                        .queryParam("active", "false")
                        .queryParam("projection", "summary")
                        .queryParam("size", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[?(@.username == 'query-inactive')]").isNotEmpty())
                .andExpect(jsonPath("$.data.items[0].active").doesNotExist());

        mockMvc.perform(get("/api/customers")
                        .header("Authorization", "Bearer " + readToken)
                        .queryParam("page", "1000")
                        .queryParam("size", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.page").value(1000))
                .andExpect(jsonPath("$.data.size").value(100))
                .andExpect(jsonPath("$.data.items").isEmpty());
    }

    private void create(String token, String username, String email, String displayName) throws Exception {
        mockMvc.perform(post("/api/customers")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"email\":\"" + email + "\",\"displayName\":\"" + displayName + "\"}"))
                .andExpect(status().isOk());
    }

    private String accessToken(String scope) throws Exception {
        MvcResult result = mockMvc.perform(post("/oauth2/token")
                        .with(httpBasic(CLIENT_ID, CLIENT_SECRET))
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "client_credentials")
                        .param("scope", scope))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.access_token").isString())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.access_token");
    }
}
