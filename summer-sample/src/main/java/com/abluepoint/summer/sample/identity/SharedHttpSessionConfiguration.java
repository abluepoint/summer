package com.abluepoint.summer.sample.identity;

import java.time.Duration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.session.config.SessionRepositoryCustomizer;
import org.springframework.session.data.redis.RedisSessionRepository;
import org.springframework.session.data.redis.config.annotation.web.http.EnableRedisHttpSession;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.session.web.http.DefaultCookieSerializer;

/** Explicitly enable the existing Spring Session dependency only in shared-login mode. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "summer.security.storage", name = "session", havingValue = "redis")
@EnableRedisHttpSession(redisNamespace = "${spring.session.data.redis.namespace:summer:servlet-session}")
class SharedHttpSessionConfiguration {
    @Bean SessionRepositoryCustomizer<RedisSessionRepository> sessionTimeout(Environment environment) {
        return repository -> repository.setDefaultMaxInactiveInterval(environment.getProperty(
                "server.servlet.session.timeout", Duration.class, Duration.ofMinutes(30)));
    }

    @Bean CookieSerializer sessionCookieSerializer(Environment environment) {
        var cookie = new DefaultCookieSerializer();
        cookie.setCookieName(environment.getProperty("server.servlet.session.cookie.name", "JSESSIONID"));
        cookie.setCookiePath(environment.getProperty("server.servlet.session.cookie.path", "/"));
        cookie.setUseHttpOnlyCookie(true);
        cookie.setSameSite("Lax");
        Boolean secure = environment.getProperty("server.servlet.session.cookie.secure", Boolean.class);
        if (secure != null) cookie.setUseSecureCookie(secure);
        return cookie;
    }
}
