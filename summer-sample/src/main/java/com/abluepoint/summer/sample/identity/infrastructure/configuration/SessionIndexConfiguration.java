package com.abluepoint.summer.sample.identity.infrastructure.configuration;

import com.abluepoint.summer.sample.identity.application.SessionIndexService;
import com.abluepoint.summer.sample.identity.infrastructure.security.RedisSummerSessionStore;

import com.abluepoint.summer.security.session.spi.SummerSessionStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(SessionIndexProperties.class)
@org.springframework.context.annotation.Import({SharedHttpSessionConfiguration.class, SessionFailureConfiguration.class})
@ConditionalOnProperty(prefix = "summer.sample.session-index", name = "enabled", havingValue = "true")
public class SessionIndexConfiguration {

    @Bean
    @ConditionalOnProperty(prefix = "summer.security.storage", name = "session", havingValue = "redis")
    SummerSessionStore redisSummerSessionStore(SessionIndexService index) {
        return new RedisSummerSessionStore(index);
    }
}

/**
 * Redis is authoritative for authentication. The servlet session identifier is
 * supplied by the servlet/Spring Session layer; local contexts never override
 * missing, expired, revoked or unavailable shared records.
 */
