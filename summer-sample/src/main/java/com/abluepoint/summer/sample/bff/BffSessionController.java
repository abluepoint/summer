package com.abluepoint.summer.sample.bff;

import com.abluepoint.summer.common.error.SummerException;
import com.abluepoint.summer.sample.error.SampleTransportErrorCode;
import jakarta.servlet.http.Cookie;
import com.abluepoint.summer.mvc.api.RawResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;

import java.time.Instant;
import java.util.Map;

@RestController
@Profile("bff-runtime")
final class BffSessionController {

    private final BffSessionService sessionService;
    private final BffCookiePolicy cookiePolicy;
    private final SummerMcpClient summerMcpClient;

    BffSessionController(BffSessionService sessionService, BffCookiePolicy cookiePolicy) {
        this(sessionService, cookiePolicy, new UnavailableSummerMcpClient());
    }

    @Autowired
    BffSessionController(BffSessionService sessionService, BffCookiePolicy cookiePolicy, SummerMcpClient summerMcpClient) {
        this.sessionService = sessionService;
        this.cookiePolicy = cookiePolicy;
        this.summerMcpClient = summerMcpClient;
    }

    @GetMapping("/bff/auth/start")
    ResponseEntity<Void> start() {
        BffSessionService.StartResult result = sessionService.start();
        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, cookiePolicy.authorizationUri(result.state(), result.codeChallenge()))
                .header(HttpHeaders.SET_COOKIE, cookiePolicy.stateCookie(result.state()))
                .build();
    }

    @GetMapping("/bff/auth/callback")
    ResponseEntity<Void> callback(
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String state,
            HttpServletRequest request
    ) {
        String stateCookie = cookie(request, BffCookiePolicy.OIDC_STATE_COOKIE);
        BffSessionService.CallbackResult result = sessionService.callback(code, state, stateCookie);
        if (result == null) {
            return ResponseEntity.badRequest()
                    .header(HttpHeaders.SET_COOKIE, cookiePolicy.clearCookie(BffCookiePolicy.OIDC_STATE_COOKIE, true))
                    .build();
        }
        return ResponseEntity.status(HttpStatus.SEE_OTHER)
                .header(HttpHeaders.LOCATION, "/")
                .header(HttpHeaders.SET_COOKIE, cookiePolicy.clearCookie(BffCookiePolicy.OIDC_STATE_COOKIE, true))
                .header(HttpHeaders.SET_COOKIE, cookiePolicy.productSessionCookie(result.productSession()))
                .header(HttpHeaders.SET_COOKIE, cookiePolicy.csrfCookie(result.csrfToken()))
                .build();
    }

    @DeleteMapping("/bff/session")
    ResponseEntity<Void> logout(HttpServletRequest request) {
        sessionService.logout(cookie(request, BffCookiePolicy.PRODUCT_SESSION_COOKIE));
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, cookiePolicy.clearCookie(BffCookiePolicy.PRODUCT_SESSION_COOKIE, true))
                .header(HttpHeaders.SET_COOKIE, cookiePolicy.clearCookie(BffCookiePolicy.CSRF_COOKIE, false))
                .build();
    }

    @PostMapping(value = "/bff/api/v1/enterprises/{enterpriseId}/mcp/invoke", consumes = MediaType.APPLICATION_JSON_VALUE)
    @RawResponse
    ResponseEntity<?> invokeMcp(
            @PathVariable String enterpriseId,
            @RequestBody BffMcpInvokeRequest request,
            HttpServletRequest httpRequest) {
        OidcIdentity identity = sessionService.identity(cookie(httpRequest, BffCookiePolicy.PRODUCT_SESSION_COOKIE));
        if (identity == null || identity.expiresAt() == null || !identity.expiresAt().isAfter(Instant.now())) {
            throw SummerException.withMessage(BffErrorCode.SESSION_REQUIRED, "Product session is missing or expired");
        }
        if (enterpriseId == null || enterpriseId.isBlank() || !identity.enterpriseIds().contains(enterpriseId)) {
            throw SummerException.withMessage(BffErrorCode.ENTERPRISE_CONTEXT_DENIED, "Enterprise context is not available in the Product Session");
        }
        if (request == null || blank(request.connectionId()) || blank(request.tool())) {
            throw SummerException.withMessage(BffErrorCode.MCP_REQUEST_INVALID, "connectionId and tool are required");
        }
        if (request.agentId() != null && !request.agentId().isBlank()) {
            throw SummerException.withMessage(BffErrorCode.AGENT_ID_FORBIDDEN, "agentId is derived by the server and cannot be supplied by the browser");
        }
        try {
            return ResponseEntity.ok(Map.of("data", summerMcpClient.invoke(enterpriseId, identity, request)));
        } catch (RuntimeException upstreamFailure) {
            if (upstreamFailure instanceof SummerException business
                    && business.errorCode() == SampleTransportErrorCode.BFF_UNAVAILABLE) {
                throw business;
            }
            // The adapter boundary exposes only its declared safe failure message.
            throw SummerException.withMessage(SampleTransportErrorCode.BFF_UPSTREAM_ERROR,
                    "MCP execution is temporarily unavailable", upstreamFailure);
        }
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static String cookie(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (name.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }
}
