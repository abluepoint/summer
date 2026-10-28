package com.abluepoint.summer.sample.plugin.infrastructure;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
@org.springframework.stereotype.Component
public final class PluginInsightsQueryService {
 private final JdbcTemplate jdbc;
 public PluginInsightsQueryService(JdbcTemplate jdbc) { this.jdbc=jdbc; }
 public long activeCustomers() { return Optional.ofNullable(jdbc.queryForObject("select count(*) from summer_customer where active = true",Long.class)).orElse(0L); }
 public long auditRevision(String enterpriseId) { return Optional.ofNullable(jdbc.queryForObject("select count(*) from summer_audit_event where tenant_id = ? and resource = ?",Long.class,enterpriseId,"plugin:customer-insights")).orElse(0L); }
}
