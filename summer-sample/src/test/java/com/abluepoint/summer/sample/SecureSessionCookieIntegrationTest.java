package com.abluepoint.summer.sample;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "server.servlet.session.cookie.secure=true")
class SecureSessionCookieIntegrationTest {

    @Value("${local.server.port}")
    int port;

    @Test
    void httpsDeploymentSettingMarksSessionCookieSecure() throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/csrf"))
                .GET().build();
        HttpResponse<Void> response = HttpClient.newHttpClient()
                .send(request, HttpResponse.BodyHandlers.discarding());
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Set-Cookie").orElseThrow())
                .contains("JSESSIONID=")
                .contains("HttpOnly")
                .contains("SameSite=Lax")
                .contains("Secure");
    }
}
