package com.abluepoint.summer.sample;

import jakarta.servlet.ServletContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SessionCookieIntegrationTest {

    @Value("${local.server.port}")
    int port;

    @Autowired
    ServletContext servletContext;

    @Test
    void developmentSessionCookieIsHttpOnlySameSiteLaxAndNotSecure() throws Exception {
        assertThat(servletContext.getSessionTimeout()).isEqualTo(30);
        String cookie = sessionCookie();
        assertThat(cookie).contains("JSESSIONID=")
                .contains("HttpOnly")
                .contains("SameSite=Lax")
                .doesNotContain("Secure");
    }

    String sessionCookie() throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/csrf"))
                .GET().build();
        HttpResponse<Void> response = HttpClient.newHttpClient()
                .send(request, HttpResponse.BodyHandlers.discarding());
        assertThat(response.statusCode()).isEqualTo(200);
        return response.headers().firstValue("Set-Cookie").orElseThrow();
    }
}
