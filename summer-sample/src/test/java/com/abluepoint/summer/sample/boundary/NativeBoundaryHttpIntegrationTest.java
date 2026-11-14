package com.abluepoint.summer.sample.boundary;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.assertj.core.api.Assertions.assertThat;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Exercises native MVC resource handling through the assembled application. */
@SpringBootTest(properties = "summer.security.storage.keys=memory")
@AutoConfigureMockMvc
@org.springframework.context.annotation.Import({com.abluepoint.summer.sample.authorization.CurrentManagedAuthorizationTestFixture.class, NativeBoundaryHttpIntegrationTest.CommittedEndpoint.class})
@org.springframework.test.annotation.DirtiesContext
class NativeBoundaryHttpIntegrationTest {
    @Autowired MockMvc mvc;

    @org.springframework.beans.factory.annotation.Autowired
    com.abluepoint.summer.sample.authorization.CurrentManagedAuthorizationTestFixture current;

    @org.junit.jupiter.api.BeforeEach
    void grantCurrentInterfaces() throws Exception {
        current.grantHandlers(com.abluepoint.summer.security.authorization.ResourceAuthorization.Domain.platform(), "boundary-reader",
                (handler, path, method) -> handler.getBeanType().getName().equals(
                        "com.abluepoint.summer.sample.customer.CustomerController")
                        && method.equals("GET") && path.equals("/api/customers"));
    }

    @Test void authenticationAndMethodSecurityKeepTheirNativePriorityAndCorrelation() throws Exception {
        mvc.perform(get("/api/customers").header("X-Request-Id", "native-anonymous"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.error.code").value("SUMMER_UNAUTHORIZED"))
                .andExpect(jsonPath("$.requestId").value("native-anonymous"))
                .andExpect(header().string("X-Request-Id", "native-anonymous"));
        // The current resource filter rejects an unassigned session before MVC.
        // A separately granted session below must still reach the positive response.
        var denied = mvc.perform(get("/api/customers")
                        .with(user("boundary-denied").authorities(new SimpleGrantedAuthority("SCOPE_unrelated")))
                        .header("X-Request-Id", "native-method"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("SUMMER_FORBIDDEN"))
                .andExpect(jsonPath("$.requestId").value("native-method"))
                .andExpect(header().string("X-Request-Id", "native-method"))
                .andReturn();
        assertThat(denied.getResolvedException()).isNull();
        assertThat(denied.getResponse().getContentAsString()).doesNotContain("stackTrace", "AuthorizationDeniedException");
        mvc.perform(get("/api/customers").with(user("boundary-reader")
                        .authorities(new SimpleGrantedAuthority("SCOPE_customer.read"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true));
        // A current role cannot manufacture a delegated bearer scope.
        mvc.perform(get("/api/customers").with(
                        org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt()
                                .jwt(token -> token.subject("boundary-reader").claim("scope", "unrelated"))
                                .authorities(new SimpleGrantedAuthority("SCOPE_customer.read"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("SUMMER_FORBIDDEN"));
    }

    @Test void committedTechnicalFailureDoesNotWriteAnotherEnvelope() throws Exception {
        assertCommittedResponse("technical");
    }

    @Test void committedSummerFailureDoesNotWriteAnotherEnvelope() throws Exception {
        assertCommittedResponse("summer");
    }

    private void assertCommittedResponse(String kind) throws Exception {
        // MockMvc has no container error dispatch; an already-committed failure must
        // propagate to its caller. NativeBoundaryTcpIntegrationTest checks wire bytes.
        var thrown = org.junit.jupiter.api.Assertions.assertThrows(jakarta.servlet.ServletException.class,
                () -> mvc.perform(get("/boundary/committed/" + kind)));
        assertThat(thrown.getCause()).isInstanceOf(kind.equals("summer")
                ? com.abluepoint.summer.common.error.SummerException.class : IllegalStateException.class);
    }

    // Test-only HTTP fixture exercising Servlet's committed-response contract; no
    // production endpoint or collaborator is added/replaced.
    @org.springframework.boot.test.context.TestComponent
    @org.springframework.web.bind.annotation.RestController
    static class CommittedEndpoint {
        @org.springframework.web.bind.annotation.GetMapping("/boundary/native-problem")
        org.springframework.http.ProblemDetail problem() {
            return org.springframework.http.ProblemDetail.forStatusAndDetail(
                    org.springframework.http.HttpStatus.NOT_FOUND, "Native resource is absent");
        }

        @org.springframework.web.bind.annotation.GetMapping("/boundary/committed/{kind}")
        void stream(@org.springframework.web.bind.annotation.PathVariable String kind,
                    jakarta.servlet.http.HttpServletResponse response) throws java.io.IOException {
            response.setContentType("application/json");
            response.getOutputStream().write("already-streamed\n".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            response.flushBuffer();
            if (kind.equals("summer")) throw new com.abluepoint.summer.common.error.SummerException(
                    com.abluepoint.summer.common.error.CommonErrorCode.NOT_FOUND);
            throw new IllegalStateException("private committed diagnostic");
        }
    }

    @Test void nativeProblemDetailKeepsItsStatusAndMediaTypeWithoutABusinessEnvelope() throws Exception {
        mvc.perform(get("/boundary/native-problem"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.detail").value("Native resource is absent"))
                .andExpect(jsonPath("$.success").doesNotExist());
    }

    @Test void staticResourceRangeAndHeadKeepTheirNativeTransferSemantics() throws Exception {
        mvc.perform(get("/boundary/native-transfer.txt"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/plain"))
                .andExpect(header().string("Accept-Ranges", "bytes"))
                .andExpect(content().string("native-resource-0123456789\n"));
        mvc.perform(get("/boundary/native-transfer.txt").header("Range", "bytes=0-5"))
                .andExpect(status().isPartialContent())
                .andExpect(header().string("Content-Range", "bytes 0-5/27"))
                .andExpect(content().string("native"));
        mvc.perform(head("/boundary/native-transfer.txt"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Length", "27"))
                .andExpect(content().string(""));
        mvc.perform(get("/boundary/native-transfer.txt").header("Range", "bytes=99-100"))
                .andExpect(status().isRequestedRangeNotSatisfiable())
                .andExpect(header().string("Content-Range", "bytes */27"))
                .andExpect(content().string(""));
    }

    @Test void missingStaticResourceRemainsANativeNotFound() throws Exception {
        mvc.perform(get("/boundary/no-such-native-file.txt"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").doesNotExist())
                .andExpect(jsonPath("$.error.code").doesNotExist());
    }
}
