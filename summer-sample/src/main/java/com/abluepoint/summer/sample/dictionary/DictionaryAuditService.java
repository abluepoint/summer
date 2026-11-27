package com.abluepoint.summer.sample.dictionary;

import com.abluepoint.summer.security.authorization.ResourceAuthorization.Domain;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
class DictionaryAuditService {
    private final JdbcTemplate jdbcTemplate;

    DictionaryAuditService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    void recordSuccess(String code, String action, String requestId, Authentication authentication) {
        String principal = authentication == null || authentication.getName() == null
                ? "system" : authentication.getName();
        jdbcTemplate.update("""
                insert into summer_audit_event
                    (event_id, tenant_id, organization_id, principal, resource, action, result, request_id, detail_json, occurred_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                UUID.randomUUID().toString(), Domain.platform().id(), null, principal, "dictionary:" + code,
                action, "SUCCESS", requestId, "{}", Instant.now());
    }
}
