package com.abluepoint.summer.sample.authorization.api.permission;

import com.abluepoint.summer.sample.authorization.infrastructure.permission.ManagedAuthorizationBootstrap;
import com.abluepoint.summer.security.authorization.ResourceAuthorization.Domain;
import java.lang.reflect.InvocationTargetException;
import java.util.Set;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;

/** Trusted test setup only. Product startup still validates and never installs or repairs domains. */
@TestConfiguration(proxyBeanMethods = false)
public class TrustedManagedTestFixture {
  @Bean
  FreshManagedDomains trustedManagedDomains(ManagedAuthorizationBootstrap bootstrap, JdbcTemplate db,
      DataSource source, @Value("${summer.sample.authorization.managed-bootstrap-domains:}") String domains) {
    return new FreshManagedDomains(bootstrap, db, source, domains);
  }

  record FreshManagedDomains(ManagedAuthorizationBootstrap bootstrap, JdbcTemplate db,
      DataSource source, String domains) {
    @EventListener(ApplicationReadyEvent.class)
    @Order(1)
    public void prepare() throws Exception {
      if (domains.isBlank()) return;
      try (var connection = source.getConnection()) {
        if (!connection.getMetaData().getURL().startsWith("jdbc:h2:mem:"))
          throw new IllegalStateException("Managed test fixture requires its in-memory test database");
      }
      var seed = ManagedAuthorizationBootstrap.class.getDeclaredMethod("seedNewDomain", Domain.class);
      seed.setAccessible(true);
      for (String key : domains.split(",")) {
        key = key.trim();
        if (!Set.of("PLATFORM:platform", "ENTERPRISE:ent-acme").contains(key))
          throw new IllegalStateException("Unapproved managed test domain");
        String[] parts = key.split(":", 2);
        Domain domain = new Domain(parts[0], parts[1]);
        if (db.queryForObject("select count(*) from summer_authorization_domain where domain_key=?", Integer.class, key) != 0
            || db.queryForObject("select count(*) from summer_permission_bootstrap where domain_key=?", Integer.class, key) != 0)
          throw new IllegalStateException("Managed test fixture accepts only fresh domains");
        try { seed.invoke(bootstrap, domain); }
        catch (InvocationTargetException failure) {
          throw new IllegalStateException("Managed test setup failed", failure.getCause());
        }
      }
    }
  }
}
