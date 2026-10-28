package com.abluepoint.summer.sample.bff.api;

import com.abluepoint.summer.sample.bff.application.BffSessionService;
import com.abluepoint.summer.sample.bff.domain.OidcCodeExchanger;
import com.abluepoint.summer.sample.bff.domain.OidcIdentity;
import com.abluepoint.summer.sample.bff.infrastructure.BffCsrfFilter;
import com.abluepoint.summer.sample.bff.infrastructure.BffTokenGenerator;
import com.abluepoint.summer.sample.bff.infrastructure.InMemoryBffStateStore;
import com.abluepoint.summer.sample.bff.infrastructure.InMemoryProductSessionStore;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class BffSessionControllerTest {

    private static final String ORIGIN = "https://console.example.test";

    private final InMemoryBffStateStore stateStore = new InMemoryBffStateStore();
    private final InMemoryProductSessionStore sessionStore = new InMemoryProductSessionStore();
    private final RecordingOidcCodeExchanger exchanger = new RecordingOidcCodeExchanger();
    private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new BffSessionController(
                    new BffSessionService(
                            stateStore,
                            sessionStore,
                            exchanger,
                            new BffTokenGenerator()
                    ),
                    new BffCookiePolicy("https://console.example.test/", "/")))
            .addFilters(new BffCsrfFilter(List.of(ORIGIN)))
            .build();

    @Test
    void startsOidcAndEstablishesHttpOnlyProductSessionAfterOneTimeCallback() throws Exception {
        MvcResult start = mockMvc.perform(get("/bff/auth/start"))
                .andExpect(status().isFound())
                .andExpect(header().string(HttpHeaders.LOCATION,
                        org.hamcrest.Matchers.startsWith("https://console.example.test/oauth2/authorize?")))
                .andReturn();

        String stateCookie = cookieValue(start.getResponse(), BffCookiePolicy.OIDC_STATE_COOKIE);
        String state = queryParameter(start.getResponse().getHeader(HttpHeaders.LOCATION), "state");
        assertThat(start.getResponse().getHeader(HttpHeaders.LOCATION))
                .contains("code_challenge_method=S256")
                .contains("code_challenge=");

        MvcResult callback = mockMvc.perform(get("/bff/auth/callback")
                        .param("code", "code-from-provider")
                        .param("state", state)
                        .cookie(new jakarta.servlet.http.Cookie(BffCookiePolicy.OIDC_STATE_COOKIE, stateCookie)))
                .andExpect(status().isSeeOther())
                .andExpect(header().string(HttpHeaders.LOCATION, "/"))
                .andReturn();

        String productCookie = cookieValue(callback.getResponse(), BffCookiePolicy.PRODUCT_SESSION_COOKIE);
        String csrfCookie = cookieValue(callback.getResponse(), BffCookiePolicy.CSRF_COOKIE);
        assertThat(callback.getResponse().getHeaders(HttpHeaders.SET_COOKIE))
                .anySatisfy(value -> assertThat(value)
                        .contains(BffCookiePolicy.PRODUCT_SESSION_COOKIE + "=")
                        .contains("HttpOnly")
                        .contains("Secure")
                        .contains("SameSite=Lax"));
        assertThat(csrfCookie).isNotBlank();
        assertThat(productCookie).isNotBlank();
        assertThat(exchanger.lastCode).isEqualTo("code-from-provider");
        assertThat(exchanger.lastCodeVerifier).isNotBlank();

        mockMvc.perform(get("/bff/auth/callback")
                        .param("code", "replay")
                        .param("state", state)
                        .cookie(new jakarta.servlet.http.Cookie(BffCookiePolicy.OIDC_STATE_COOKIE, stateCookie)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsLogoutWithoutMatchingCsrfCookieHeaderAndOrigin() throws Exception {
        mockMvc.perform(delete("/bff/session")
                        .header("Origin", ORIGIN)
                        .header("X-CSRF-Token", "missing-cookie"))
                .andExpect(status().isForbidden());
    }

    @Test
    void rejectsLogoutFromAnUntrustedOriginEvenWhenCsrfPairMatches() throws Exception {
        mockMvc.perform(delete("/bff/session")
                        .header("Origin", "https://evil.example.test")
                        .header("X-CSRF-Token", "same-value")
                        .cookie(new jakarta.servlet.http.Cookie(BffCookiePolicy.CSRF_COOKIE, "same-value")))
                .andExpect(status().isForbidden());
    }

    @Test
    void rejectsCallbackWhenStateDoesNotMatchTheBrowserCookie() throws Exception {
        MvcResult start = mockMvc.perform(get("/bff/auth/start")).andReturn();
        String stateCookie = cookieValue(start.getResponse(), BffCookiePolicy.OIDC_STATE_COOKIE);

        mockMvc.perform(get("/bff/auth/callback")
                        .param("code", "code")
                        .param("state", "attacker-state")
                        .cookie(new jakarta.servlet.http.Cookie(BffCookiePolicy.OIDC_STATE_COOKIE, stateCookie)))
                .andExpect(status().isBadRequest());
        assertThat(sessionStore.size()).isZero();
    }

    @Test
    void clearsProductSessionAndCsrfCookiesAfterValidLogout() throws Exception {
        MvcResult start = mockMvc.perform(get("/bff/auth/start")).andReturn();
        String stateCookie = cookieValue(start.getResponse(), BffCookiePolicy.OIDC_STATE_COOKIE);
        String state = queryParameter(start.getResponse().getHeader(HttpHeaders.LOCATION), "state");
        MvcResult callback = mockMvc.perform(get("/bff/auth/callback")
                        .param("code", "code")
                        .param("state", state)
                        .cookie(new jakarta.servlet.http.Cookie(BffCookiePolicy.OIDC_STATE_COOKIE, stateCookie)))
                .andReturn();
        String productCookie = cookieValue(callback.getResponse(), BffCookiePolicy.PRODUCT_SESSION_COOKIE);
        String csrfCookie = cookieValue(callback.getResponse(), BffCookiePolicy.CSRF_COOKIE);

        MockHttpServletResponse response = mockMvc.perform(delete("/bff/session")
                        .header("Origin", ORIGIN)
                        .header("X-CSRF-Token", csrfCookie)
                        .cookie(
                                new jakarta.servlet.http.Cookie(BffCookiePolicy.PRODUCT_SESSION_COOKIE, productCookie),
                                new jakarta.servlet.http.Cookie(BffCookiePolicy.CSRF_COOKIE, csrfCookie)))
                .andExpect(status().isNoContent())
                .andReturn()
                .getResponse();

        assertThat(response.getHeaders(HttpHeaders.SET_COOKIE))
                .anySatisfy(value -> assertThat(value).contains(BffCookiePolicy.PRODUCT_SESSION_COOKIE + "=").contains("Max-Age=0"));
        assertThat(response.getHeaders(HttpHeaders.SET_COOKIE))
                .anySatisfy(value -> assertThat(value).contains(BffCookiePolicy.CSRF_COOKIE + "=").contains("Max-Age=0"));
        assertThat(sessionStore.size()).isZero();
    }

    private static String cookieValue(MockHttpServletResponse response, String name) {
        return response.getHeaders(HttpHeaders.SET_COOKIE).stream()
                .map(value -> value.substring(0, value.indexOf(';')))
                .filter(value -> value.startsWith(name + "="))
                .map(value -> value.substring(name.length() + 1))
                .findFirst()
                .orElseThrow();
    }

    private static String queryParameter(String location, String name) {
        return java.util.Arrays.stream(location.substring(location.indexOf('?') + 1).split("&"))
                .map(pair -> pair.split("=", 2))
                .filter(pair -> pair[0].equals(name))
                .map(pair -> java.net.URLDecoder.decode(pair[1], java.nio.charset.StandardCharsets.UTF_8))
                .findFirst()
                .orElseThrow();
    }

    private static final class RecordingOidcCodeExchanger implements OidcCodeExchanger {
        private String lastCode;
        private String lastCodeVerifier;

        @Override
        public OidcIdentity exchange(String code, String codeVerifier) {
            lastCode = code;
            lastCodeVerifier = codeVerifier;
            return new OidcIdentity("subject-1", "Alice", Instant.parse("2030-01-01T00:00:00Z"));
        }
    }
}
