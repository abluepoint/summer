package com.abluepoint.summer.sample.authorization.api.permission;

import static org.junit.jupiter.api.Assertions.*;

import com.abluepoint.summer.sample.authorization.application.data.DataReadService;
import com.abluepoint.summer.sample.authorization.application.data.DataResourceRegistry;
import com.abluepoint.summer.security.authorization.ResourceAuthorization.Domain;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.access.AccessDeniedException;

/** Projection constraints only; authority and real business HTTP are verified separately. */
class RegisteredScopeProjectionTest {
  private final DataResourceRegistry registry = new DataResourceRegistry();

  @Test void trustedProjectionBindsTenantAndUnionsOwnerWithEvaluatedDepartmentsBeforePagination() {
    var sql = new NamedParameterJdbcTemplate(new DriverManagerDataSource("jdbc:h2:mem:scope10;DB_CLOSE_DELAY=-1", "sa", ""));
    sql.getJdbcTemplate().execute("create table scope10_rows(id int,tenant_id varchar(80),owner_id varchar(80),created_by varchar(80),organization_id varchar(80),active boolean)");
    sql.getJdbcTemplate().execute("insert into scope10_rows values (1,'platform','alice','bob','d1',true), (2,'platform','bob','alice','d2',true), (3,'platform','bob','alice','other',true), (4,'other','alice','alice','d2',true), (5,'platform','alice','alice','d2',false)");
    var scope = new DataReadService.Scope(Domain.enterprise("platform"), registry.require("dictionaries", "read"), 17, false, true, "alice", Set.of("d2"));
    var projection = registry.configurationProjection(scope);
    assertFalse(projection.bindings().containsKey("scopeDomain"));
    assertEquals(2, sql.queryForObject("select count(*) from scope10_rows where active=true and " + projection.predicate(), projection.bindings(), Integer.class));
    assertEquals(java.util.List.of(2), sql.queryForList("select id from scope10_rows where active=true and " + projection.predicate() + " order by id limit 1 offset 1", projection.bindings(), Integer.class));
    assertFalse(projection.allows("other", "bob"));
    assertTrue(projection.allows(null, "alice"));
    assertTrue(projection.fingerprint().contains("ENTERPRISE:platform"));
    assertTrue(projection.fingerprint().contains(":17:"));
  }

  @Test void sameTenantStringNeverLetsPlatformScopeEnterEnterpriseOnlyMapping() {
    var scope = new DataReadService.Scope(Domain.platform(), registry.require("dictionaries", "read"), 1, true, false, null);
    assertThrows(AccessDeniedException.class, () -> registry.configurationProjection(scope));
  }

  @Test void emptyRangeIsNotReplacedWithAnAllRowsConstant() {
    var scope = new DataReadService.Scope(Domain.enterprise("ent-acme"), registry.require("system-parameters", "read"), 2, false, false, null);
    var projection = registry.configurationProjection(scope);
    assertFalse(projection.allows(null, null));
    assertTrue(projection.predicate().contains("1=0"));
  }
}
