package com.abluepoint.summer.security.autoconfigure;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Exposes the framework-managed CSRF token for the browser form-login flow. */
@RestController
final class SummerCsrfEndpoint {

    @GetMapping("/csrf")
    CsrfToken csrf(CsrfToken token, HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("Pragma", "no-cache");
        return token;
    }
}
