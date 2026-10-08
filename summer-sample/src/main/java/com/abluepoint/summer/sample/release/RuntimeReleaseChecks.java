package com.abluepoint.summer.sample.release;

import java.util.*;
import java.util.function.BooleanSupplier;
import org.flywaydb.core.Flyway;
import org.quartz.Scheduler;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import com.abluepoint.summer.security.autoconfigure.storage.SummerKeyStore;
import com.nimbusds.jose.jwk.JWKMatcher;
import com.nimbusds.jose.jwk.JWKSelector;

/** Runtime prerequisites only. CI, browser, backup and rolling evidence are separate gates. */
@Component
class RuntimeReleaseChecks {
    private final JdbcTemplate jdbc;
    private final StringRedisTemplate redis;
    private final ObjectProvider<Flyway> migrations;
    private final ObjectProvider<Scheduler> schedulers;
    private final ObjectProvider<SummerKeyStore> keys;
    private final Environment env;
    private final ApplicationContext context;
    RuntimeReleaseChecks(JdbcTemplate jdbc, StringRedisTemplate redis, ObjectProvider<Flyway> migrations,
            ObjectProvider<Scheduler> schedulers, ObjectProvider<SummerKeyStore> keys,
            Environment env, ApplicationContext context) {
        this.jdbc=jdbc; this.redis=redis; this.migrations=migrations; this.schedulers=schedulers;
        this.keys=keys; this.env=env; this.context=context;
    }

    List<ReadinessCheck> snapshot() {
        List<ReadinessCheck> checks=new ArrayList<>();
        checks.add(check("database", () -> Integer.valueOf(1).equals(jdbc.queryForObject("select 1",Integer.class)), "SQL connection"));
        checks.add(check("migrations", () -> {
            Flyway flyway=migrations.getIfAvailable();
            return flyway!=null && flyway.validateWithResult().validationSuccessful
                    && flyway.info().pending().length==0 && flyway.info().applied().length>0;
        }, "Flyway validation and pending migrations"));
        checks.add(check("configuration", () -> "redis".equals(env.getProperty("summer.security.storage.session"))
                && "jdbc".equals(env.getProperty("summer.security.storage.oauth-client"))
                && "jdbc".equals(env.getProperty("summer.security.storage.authorization"))
                && !"memory".equals(env.getProperty("summer.security.storage.keys","memory")), "shared persistence selected"));
        checks.add(check("keys", () -> {
            try {
                var store=keys.getIfAvailable();
                if(store==null)return false;
                var publicKeys=store.get(new JWKSelector(new JWKMatcher.Builder().build()),null);
                return !publicKeys.isEmpty() && publicKeys.stream().allMatch(k -> k.getKeyID()!=null);
            } catch(Exception failure) { return false; }
        }, "signing key source readable; key material omitted"));
        checks.add(check("authorization", () -> count("summer_authorization_role")>=0
                && count("summer_authorization_user_binding")>=0 && count("summer_organization_context")>=0,
                "authorization schema readable; grant matrix requires separate evidence"));
        checks.add(check("tasks", () -> {
            try { Scheduler s=schedulers.getIfAvailable();return s!=null && s.isStarted() && !s.isInStandbyMode() && s.getMetaData().isJobStoreClustered(); }
            catch(Exception failure) { return false; }
        }, "running clustered Quartz scheduler"));
        boolean redisAvailable;
        try(var connection=redis.getConnectionFactory().getConnection()) { redisAvailable="PONG".equals(connection.ping()); }
        catch(RuntimeException unavailable) { redisAvailable=false; }
        final boolean redisUp=redisAvailable;
        checks.add(check("cache", () -> redisUp && context.containsBean("versionedTwoLevelCache"), "Redis ping and local cache configured"));
        checks.add(check("events", () -> redisUp
                && env.getProperty("summer.sample.authorization-events.scheduling-enabled",Boolean.class,false)
                && env.getProperty("summer.sample.parameter-events.scheduling-enabled",Boolean.class,false),
                "Redis reachable and event pollers enabled; delivery evidence is separate"));
        checks.add(check("realtime", () -> redisUp && context.containsBean("realtimeWebSocketConfiguration")
                && env.getProperty("summer.sample.realtime.redis-enabled",Boolean.class,false),
                "cross-node relay configured; protocol evidence is separate"));
        checks.add(check("plugins", () -> context.containsBean("pluginCatalog")
                && jdbc.queryForObject("select count(*) from summer_plugin where status='INCOMPATIBLE' or (status='ENABLED' and trusted_flag=false)",Long.class)==0,
                "catalog loaded and no blocked registered plugin"));
        return List.copyOf(checks);
    }
    private long count(String table) { return jdbc.queryForObject("select count(*) from "+table,Long.class); }
    private static ReadinessCheck check(String name,BooleanSupplier work,String description) {
        boolean passed;
        try { passed=work.getAsBoolean(); } catch(RuntimeException failure) { passed=false; }
        return new ReadinessCheck(name,passed,description+(passed?": available":": unavailable or not configured"));
    }
}
