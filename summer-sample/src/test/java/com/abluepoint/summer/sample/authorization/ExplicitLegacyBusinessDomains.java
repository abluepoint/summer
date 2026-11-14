package com.abluepoint.summer.sample.authorization;

import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;

/** Explicit old-engine fixtures only; never imported by managed or production initialization. */
public final class ExplicitLegacyBusinessDomains {
  private ExplicitLegacyBusinessDomains() {}
  private static void install(JdbcTemplate db,String domain) {
    db.update("insert into summer_authorization_domain(domain_key,engine_mode,revision) select ?,'LEGACY',0 where not exists (select 1 from summer_authorization_domain where domain_key=?)",domain,domain);
    if (!"LEGACY".equals(db.queryForObject("select engine_mode from summer_authorization_domain where domain_key=?",String.class,domain)))
      throw new IllegalStateException("Explicit legacy fixture cannot replace a managed domain");
  }
  @TestConfiguration(proxyBeanMethods=false)
  public static class EnterpriseAcme {
    @Bean ApplicationRunner explicitLegacyEnterpriseAcme(JdbcTemplate db) { return args -> install(db,"ENTERPRISE:ent-acme"); }
  }
  @TestConfiguration(proxyBeanMethods=false)
  public static class Platform {
    @Bean ApplicationRunner explicitLegacyPlatform(JdbcTemplate db) { return args -> install(db,"PLATFORM:platform"); }
  }
  @TestConfiguration(proxyBeanMethods=false)
  @org.springframework.context.annotation.Import({EnterpriseAcme.class,Platform.class})
  public static class AcmeAndPlatform {}
  @TestConfiguration(proxyBeanMethods=false)
  @org.springframework.context.annotation.Import(EnterpriseAcme.class)
  public static class AcmeAndNorth {
    @Bean ApplicationRunner explicitLegacyEnterpriseNorth(JdbcTemplate db) { return args -> install(db,"ENTERPRISE:ent-north"); }
  }
  @TestConfiguration(proxyBeanMethods=false)
  @org.springframework.context.annotation.Import(EnterpriseAcme.class)
  public static class AcmeAndOther {
    @Bean ApplicationRunner explicitLegacyEnterpriseOther(JdbcTemplate db) { return args -> install(db,"ENTERPRISE:ent-other"); }
  }

}
