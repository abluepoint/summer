package com.abluepoint.summer.sample.authorization.infrastructure;

import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.security.oauth2.server.authorization.JdbcOAuth2AuthorizationConsentService;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;

public class JdbcSummerConsentStore extends JdbcOAuth2AuthorizationConsentService
        implements com.abluepoint.summer.security.oauth2.spi.SummerConsentStore {
    JdbcSummerConsentStore(JdbcOperations jdbc, RegisteredClientRepository clients) {
        super(jdbc, clients);
    }
}
