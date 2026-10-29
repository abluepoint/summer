package com.abluepoint.summer.sample.systemparameter.application;

import com.abluepoint.summer.sample.systemparameter.domain.SystemParameterRules;

import com.abluepoint.summer.sample.systemparameter.application.command.SystemParameterDraftCommand;
import com.abluepoint.summer.sample.systemparameter.application.command.SystemParameterReleaseCommand;
import com.abluepoint.summer.sample.systemparameter.application.view.SystemParameterReleaseView;
import com.abluepoint.summer.sample.systemparameter.application.view.SystemParameterVersionPageView;
import com.abluepoint.summer.sample.systemparameter.application.view.SystemParameterVersionView;
import com.abluepoint.summer.sample.systemparameter.application.view.SystemParameterView;
import com.abluepoint.summer.sample.systemparameter.domain.SystemParameterNotFoundException;
import com.abluepoint.summer.sample.systemparameter.domain.SystemParameterReleaseNotFoundException;
import com.abluepoint.summer.sample.systemparameter.domain.SystemParameterReleaseState;
import com.abluepoint.summer.sample.systemparameter.domain.SystemParameterReleaseValidationException;
import com.abluepoint.summer.sample.systemparameter.domain.SystemParameterSnapshot;
import com.abluepoint.summer.sample.systemparameter.domain.SystemParameterStatus;
import com.abluepoint.summer.sample.systemparameter.domain.SystemParameterVersionConflictException;
import com.abluepoint.summer.sample.systemparameter.infrastructure.persistence.SystemParameterEntity;
import com.abluepoint.summer.sample.systemparameter.infrastructure.persistence.SystemParameterRepository;
import com.abluepoint.summer.sample.systemparameter.infrastructure.persistence.SystemParameterVersionEntity;
import com.abluepoint.summer.sample.systemparameter.infrastructure.persistence.SystemParameterVersionRepository;

import static com.abluepoint.summer.sample.configuration.infrastructure.ConfigurationTenantContext.resolve;
import com.abluepoint.summer.sample.authorization.application.DataScopeService;
import com.abluepoint.summer.sample.cache.service.VersionedTwoLevelCache;
import com.abluepoint.summer.sample.customer.service.CustomerAuditService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import java.util.List;

@Service
public class SystemParameterReleaseService {
    private final SystemParameterRepository parameters;
    private final SystemParameterVersionRepository versions;
    private final ObjectMapper objectMapper;
    private final SystemParameterEventService events;
    private final VersionedTwoLevelCache cache;
    private final CustomerAuditService audit;
    private final DataScopeService dataScopes;

    public SystemParameterReleaseService(SystemParameterRepository parameters, SystemParameterVersionRepository versions,
                                  ObjectMapper objectMapper, SystemParameterEventService events,
                                  VersionedTwoLevelCache cache, CustomerAuditService audit,
                                  DataScopeService dataScopes) {
        this.parameters = parameters;
        this.versions = versions;
        this.objectMapper = objectMapper;
        this.events = events;
        this.cache = cache;
        this.audit = audit;
        this.dataScopes = dataScopes;
    }

    @Transactional
    public void recordInitial(SystemParameterEntity parameter, Authentication authentication) {
        if (versions.findByTenantIdAndParameterKeyAndRevision(parameter.tenantId(), parameter.key(), 0).isPresent()) return;
        try {
            versions.saveAndFlush(new SystemParameterVersionEntity(parameter.tenantId(), parameter.key(), 0,
                    SystemParameterReleaseState.PUBLISHED, serialize(snapshot(parameter)), parameter.version(),
                    "initial:" + parameter.id(), principal(authentication)));
        } catch (DataIntegrityViolationException ignored) {
            // Another request recorded the baseline; the current parameter is still authoritative.
        }
    }

    @Transactional
    public SystemParameterReleaseView draft(String key, SystemParameterDraftCommand request, String requestId,
                                       Authentication authentication) {
        SystemParameterEntity parameter = current(key, authentication);
        String idempotency = SystemParameterRules.idempotency(request == null ? null : request.idempotencyKey());
        var existing = versions.findByTenantIdAndParameterKeyAndIdempotencyKey(parameter.tenantId(), key, idempotency);
        if (existing.isPresent()) return result(parameter, existing.get());
        if (request == null || request.baseVersion() == null || request.baseVersion() != parameter.version()) {
            throw new SystemParameterVersionConflictException();
        }
        SystemParameterSnapshot snapshot = merge(parameter, request);
        SystemParameterRules.validateSnapshot(snapshot);
        recordInitial(parameter, authentication);
        SystemParameterVersionEntity saved = versions.saveAndFlush(new SystemParameterVersionEntity(parameter.tenantId(), key,
                nextRevision(parameter.tenantId(), key), SystemParameterReleaseState.DRAFT, serialize(snapshot), parameter.version(),
                idempotency, principal(authentication)));
        audit.recordTenantResourceSuccess(resolve(authentication), "system-parameter:" + key, "SYSTEM_PARAMETER_DRAFT_SAVED", requestId, authentication);
        return result(parameter, saved);
    }

    @Transactional
    public SystemParameterReleaseView publish(String key, SystemParameterReleaseCommand command, String requestId,
                                         Authentication authentication) {
        SystemParameterEntity parameter = current(key, authentication);
        String idempotency = SystemParameterRules.idempotency(command == null ? null : command.idempotencyKey());
        var existing = versions.findByTenantIdAndParameterKeyAndIdempotencyKey(parameter.tenantId(), key, idempotency);
        if (existing.isPresent()) return result(parameter, existing.get());
        checkExpected(parameter, command);
        SystemParameterVersionEntity draft = version(parameter.tenantId(), key, command.revision());
        SystemParameterRules.publish(draft.state());
        SystemParameterSnapshot snapshot = readSnapshot(draft.snapshotJson());
        SystemParameterRules.validateSnapshot(snapshot);
        return apply(parameter, snapshot, SystemParameterReleaseState.PUBLISHED, "SYSTEM_PARAMETER_PUBLISHED",
                requestId, authentication, idempotency);
    }

    @Transactional
    public SystemParameterReleaseView rollback(String key, SystemParameterReleaseCommand command, String requestId,
                                          Authentication authentication) {
        SystemParameterEntity parameter = current(key, authentication);
        String idempotency = SystemParameterRules.idempotency(command == null ? null : command.idempotencyKey());
        var existing = versions.findByTenantIdAndParameterKeyAndIdempotencyKey(parameter.tenantId(), key, idempotency);
        if (existing.isPresent()) return result(parameter, existing.get());
        checkExpected(parameter, command);
        SystemParameterVersionEntity target = version(parameter.tenantId(), key, command.revision());
        SystemParameterRules.rollback(target.state());
        SystemParameterSnapshot snapshot = readSnapshot(target.snapshotJson());
        SystemParameterRules.validateSnapshot(snapshot);
        return apply(parameter, snapshot, SystemParameterReleaseState.ROLLED_BACK, "SYSTEM_PARAMETER_ROLLED_BACK",
                requestId, authentication, idempotency);
    }

    @Transactional
    public SystemParameterReleaseView deactivate(String key, SystemParameterReleaseCommand command, String requestId,
                                            Authentication authentication) {
        SystemParameterEntity parameter = current(key, authentication);
        String idempotency = SystemParameterRules.idempotency(command == null ? null : command.idempotencyKey());
        var existing = versions.findByTenantIdAndParameterKeyAndIdempotencyKey(parameter.tenantId(), key, idempotency);
        if (existing.isPresent()) return result(parameter, existing.get());
        checkExpected(parameter, command);
        SystemParameterSnapshot current = snapshot(parameter);
        SystemParameterSnapshot inactive = new SystemParameterSnapshot(current.type(), current.value(), current.required(),
                current.min(), current.max(), current.pattern(), current.publicValue(), current.description(),
                SystemParameterStatus.INACTIVE);
        return apply(parameter, inactive, SystemParameterReleaseState.INACTIVE, "SYSTEM_PARAMETER_DISABLED",
                requestId, authentication, idempotency);
    }

    @Transactional(readOnly = true)
    public SystemParameterVersionPageView versions(String key, Authentication authentication) {
        SystemParameterEntity parameter = current(key, authentication);
        List<SystemParameterVersionEntity> rows = versions.findAllByTenantIdAndParameterKeyOrderByRevisionAsc(parameter.tenantId(), key);
        SystemParameterReleaseState currentState = parameter.status() == SystemParameterStatus.INACTIVE
                ? SystemParameterReleaseState.INACTIVE : SystemParameterReleaseState.PUBLISHED;
        return new SystemParameterVersionPageView(rows.stream().map(row -> view(row, parameter.version())).toList(),
                parameter.version(), currentState);
    }

    private SystemParameterReleaseView apply(SystemParameterEntity parameter, SystemParameterSnapshot snapshot,
                                               SystemParameterReleaseState state, String eventType, String requestId,
                                               Authentication authentication, String idempotency) {
        parameter.update(serialize(snapshot.value()), snapshot.required(), snapshot.min(), snapshot.max(),
                snapshot.pattern(), snapshot.publicValue(), snapshot.description(), snapshot.status());
        try {
            SystemParameterEntity saved = parameters.saveAndFlush(parameter);
            SystemParameterVersionEntity release = versions.saveAndFlush(new SystemParameterVersionEntity(
                    parameter.tenantId(), parameter.key(), nextRevision(parameter.tenantId(), parameter.key()), state, serialize(snapshot), parameter.version(),
                    idempotency, principal(authentication)));
            events.enqueue(saved, eventType, requestId);
            cache.evictLocal(parameter.tenantId(), "system-parameter");
            audit.recordTenantResourceSuccess(resolve(authentication), "system-parameter:" + parameter.key(), eventType, requestId, authentication);
            return result(saved, release);
        } catch (ObjectOptimisticLockingFailureException exception) {
            throw new SystemParameterVersionConflictException();
        }
    }

    private SystemParameterEntity current(String key, Authentication authentication) {
        return parameters.findScoped(key, dataScopes.filter(resolve(authentication), "system-parameters", authentication))
                .orElseThrow(() -> new SystemParameterNotFoundException(key));
    }

    private SystemParameterVersionEntity version(String tenant, String key, Long revision) {
        if (revision == null || revision < 0) throw new SystemParameterReleaseValidationException("revision is required");
        return versions.findByTenantIdAndParameterKeyAndRevision(tenant, key, revision)
                .orElseThrow(() -> new SystemParameterReleaseNotFoundException(key, revision));
    }

    private void checkExpected(SystemParameterEntity parameter,SystemParameterReleaseCommand command) { SystemParameterRules.expected(command == null ? null : command.expectedVersion(),parameter.version()); }

    private long nextRevision(String tenant, String key) {
        return versions.findTopByTenantIdAndParameterKeyOrderByRevisionDesc(tenant, key)
                .map(value -> value.revision() + 1).orElse(0L);
    }

    private SystemParameterSnapshot merge(SystemParameterEntity parameter, SystemParameterDraftCommand request) {
        if (request == null) throw new SystemParameterReleaseValidationException("request is required");
        return new SystemParameterSnapshot(parameter.type(), request.value() == null ? deserialize(parameter.valueJson()) : request.value(),
                request.required() == null ? parameter.required() : request.required(),
                request.min() == null ? parameter.min() : request.min(),
                request.max() == null ? parameter.max() : request.max(),
                request.pattern() == null ? parameter.pattern() : blank(request.pattern()),
                request.publicValue() == null ? parameter.publicValue() : request.publicValue(),
                request.description() == null ? parameter.description() : blank(request.description()), parameter.status());
    }

    private SystemParameterSnapshot snapshot(SystemParameterEntity parameter) {
        return new SystemParameterSnapshot(parameter.type(), deserialize(parameter.valueJson()), parameter.required(),
                parameter.min(), parameter.max(), parameter.pattern(), parameter.publicValue(), parameter.description(), parameter.status());
    }

    private SystemParameterReleaseView result(SystemParameterEntity parameter, SystemParameterVersionEntity version) {
        return new SystemParameterReleaseView(new SystemParameterView(parameter.id(), parameter.key(), parameter.type(),
                deserialize(parameter.valueJson()), parameter.required(), parameter.min(), parameter.max(), parameter.pattern(),
                parameter.publicValue(), parameter.description(), parameter.status(), parameter.version(), parameter.tenantId(),
                parameter.createdAt(), parameter.updatedAt()), version.revision(), version.state());
    }

    private SystemParameterVersionView view(SystemParameterVersionEntity version, long currentVersion) {
        SystemParameterSnapshot snapshot = readSnapshot(version.snapshotJson());
        return new SystemParameterVersionView(version.revision(), version.state(), version.parameterKey(), version.baseVersion(),
                version.principal(), version.createdAt(), snapshot.value(), snapshot.status(), currentVersion);
    }

    private String serialize(Object value) {
        try { return objectMapper.writeValueAsString(value); }
        catch (JacksonException exception) { throw new IllegalArgumentException("System parameter release cannot be serialized", exception); }
    }

    private Object deserialize(String value) {
        if (value == null) return null;
        try { return objectMapper.readValue(value, Object.class); }
        catch (JacksonException exception) { throw new IllegalStateException("System parameter release cannot be read", exception); }
    }

    private SystemParameterSnapshot readSnapshot(String value) {
        try { return objectMapper.readValue(value, SystemParameterSnapshot.class); }
        catch (JacksonException exception) { throw new IllegalStateException("System parameter release cannot be read", exception); }
    }

    private static String principal(Authentication authentication) { return authentication == null ? "system" : authentication.getName(); }
    private static String blank(String value) { return value == null || value.isBlank() ? null : value; }
}
