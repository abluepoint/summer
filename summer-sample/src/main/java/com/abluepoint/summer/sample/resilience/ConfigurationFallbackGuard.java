package com.abluepoint.summer.sample.resilience;

import com.abluepoint.summer.sample.authorization.BusinessDataScopes;
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
public class ConfigurationFallbackGuard {
    private final NamedParameterJdbcTemplate jdbc;
    private final BusinessDataScopes scopes;
    private final ConfigurationActionAccess actions;

    ConfigurationFallbackGuard(NamedParameterJdbcTemplate jdbc, BusinessDataScopes scopes, ConfigurationActionAccess actions) {
        this.jdbc = jdbc;
        this.scopes = scopes;
        this.actions = actions;
    }

    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW, isolation = Isolation.READ_COMMITTED)
    public boolean parameter(String tenant, String id, long version, boolean publicValue, String fingerprint, Authentication auth) {
        actions.requirePublic(tenant, "system-parameters", auth);
        return scopes.read(tenant, "system-parameters", "read", auth, scope -> {
        if (!scope.fingerprint().equals(fingerprint)) return false;
        var bindings = new HashMap<String, Object>(scope.sqlBindings());
        bindings.put("expectedId", id);
        bindings.put("expectedVersion", version);
        bindings.put("expectedPublic", publicValue);
        return jdbc.queryForObject("select count(*) from summer_system_parameter where id=:expectedId "
                + "and version=:expectedVersion and status='ACTIVE' and public_flag=:expectedPublic and ("
                + scope.sqlPredicate() + ")", bindings, Long.class) == 1;
        });
    }

    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW, isolation = Isolation.READ_COMMITTED)
    public boolean dictionary(String tenant, String code, long version, long effectiveRevision, String fingerprint, Set<String> allowedValues, Authentication auth, Runnable authorize) {
        authorize.run();
        return scopes.read(tenant, "dictionaries", "read", auth, scope -> {
        if (!scope.fingerprint().equals(fingerprint)) return false;
        var bindings = new HashMap<String, Object>(scope.sqlBindings());
        bindings.put("expectedCode", code);
        bindings.put("expectedEffectiveRevision", effectiveRevision);
        bindings.put("expectedTenant", tenant);
        bindings.put("expectedVersion", version);
        if (jdbc.queryForObject("select count(*) from summer_dictionary_type where code=:expectedCode "
                + "and published_version=:expectedVersion and effective_revision=:expectedEffectiveRevision and published=true and ("
                + scope.sqlPredicate() + ")", bindings, Long.class) != 1) return false;
        var currentValues = jdbc.queryForList("select item_value from summer_dictionary_item where dictionary_code=:expectedCode and ("
                + scope.sqlPredicate() + ")", bindings, String.class);
        if (!Set.copyOf(currentValues).equals(allowedValues)) return false;
        return true;
        });
    }
}
