package com.abluepoint.summer.sample.resilience.service;

import com.abluepoint.summer.sample.authorization.DataScopeService;
import com.abluepoint.summer.sample.configuration.ConfigurationActionAccess;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Set;

/** Reads metadata in a fresh transaction, never the failed dependency's cached value or JPA snapshot. */
@Service
@org.springframework.modulith.NamedInterface("reads")
public class ConfigurationFallbackGuard {
    private final NamedParameterJdbcTemplate jdbc;
    private final DataScopeService scopes;
    private final ConfigurationActionAccess actions;

    ConfigurationFallbackGuard(NamedParameterJdbcTemplate jdbc, DataScopeService scopes, ConfigurationActionAccess actions) {
        this.jdbc = jdbc;
        this.scopes = scopes;
        this.actions = actions;
    }

    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW, isolation = Isolation.READ_COMMITTED)
    public boolean parameter(String tenant, String id, long version, boolean publicValue, String fingerprint, Authentication auth) {
        actions.requirePublic(tenant, "system-parameters", auth);
        var scope = scopes.filter(tenant, "system-parameters", auth);
        if (!scope.fingerprint().equals(fingerprint)) return false;
        var bindings = new HashMap<String, Object>(scope.sqlBindings());
        bindings.put("expectedId", id);
        bindings.put("expectedVersion", version);
        bindings.put("expectedPublic", publicValue);
        return jdbc.queryForObject("select count(*) from summer_system_parameter where id=:expectedId "
                + "and version=:expectedVersion and status='ACTIVE' and public_flag=:expectedPublic and ("
                + scope.sqlPredicate() + ")", bindings, Long.class) == 1;
    }

    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW, isolation = Isolation.READ_COMMITTED)
    public boolean dictionary(String tenant, String code, long version, String fingerprint, Set<String> allowedValues, Authentication auth) {
        actions.requirePublic(tenant, "dictionaries", auth);
        var scope = scopes.filter(tenant, "dictionaries", auth);
        if (!scope.fingerprint().equals(fingerprint)) return false;
        var bindings = new HashMap<String, Object>(scope.sqlBindings());
        bindings.put("expectedCode", code);
        bindings.put("expectedTenant", tenant);
        bindings.put("expectedVersion", version);
        if (jdbc.queryForObject("select count(*) from summer_dictionary_type where code=:expectedCode "
                + "and published_version=:expectedVersion and enabled=true and published=true and public_readable=true and ("
                + scope.sqlPredicate() + ")", bindings, Long.class) != 1) return false;
        var currentValues = jdbc.queryForList("select item_value from summer_dictionary_item where dictionary_code=:expectedCode and ("
                + scope.sqlPredicate() + ")", bindings, String.class);
        if (!Set.copyOf(currentValues).equals(allowedValues)) return false;
        String org = scopes.organization(tenant, auth);
        bindings.put("candidates", org == null ? List.of("_tenant") : List.of("_tenant", org));
        // Any newly applicable override invalidates the cached base value, including a hidden override.
        return jdbc.queryForObject("select count(*) from summer_configuration_override where tenant_id=:expectedTenant "
                + "and published_revision is not null and resource='dictionaries' and config_key=:expectedCode and scope_id in (:candidates)", bindings, Long.class) == 0;
    }
}
