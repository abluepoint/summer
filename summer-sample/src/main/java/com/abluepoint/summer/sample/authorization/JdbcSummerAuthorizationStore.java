package com.abluepoint.summer.sample.authorization;

import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.security.oauth2.server.authorization.JdbcOAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;

class JdbcSummerAuthorizationStore extends JdbcOAuth2AuthorizationService
        implements com.abluepoint.summer.security.autoconfigure.storage.SummerAuthorizationStore {
    JdbcSummerAuthorizationStore(JdbcOperations jdbc, RegisteredClientRepository clients) {
        super(jdbc, clients);
    }
}
