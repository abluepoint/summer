package com.abluepoint.summer.sample.systemparameter;

import static com.abluepoint.summer.sample.configuration.ConfigurationTenantContext.resolve;
import com.abluepoint.summer.common.error.SummerException;
import com.abluepoint.summer.common.error.CommonErrorCode;
import com.abluepoint.summer.mvc.validate.FieldValidationException;
import com.abluepoint.summer.sample.authorization.DataScopeService;
import com.abluepoint.summer.sample.cache.VersionedTwoLevelCache;
import com.abluepoint.summer.sample.customer.CustomerAuditService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

@Service
class SystemParameterReleaseService {
    private final SystemParameterRepository parameters;
    private final SystemParameterVersionRepository versions;
    private final ObjectMapper objectMapper;
    private final SystemParameterEventService events;
    private final VersionedTwoLevelCache cache;
    private final CustomerAuditService audit;
    private final DataScopeService dataScopes;

    SystemParameterReleaseService(SystemParameterRepository parameters, SystemParameterVersionRepository versions,
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
    void recordInitial(SystemParameterEntity parameter, Authentication authentication) {
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
    SystemParameterReleaseResult draft(String key, SystemParameterDraftRequest request, String requestId,
                                       Authentication authentication) {
        SystemParameterEntity parameter = current(key, authentication);
        String idempotency = idempotency(request == null ? null : request.idempotencyKey());
        var existing = versions.findByTenantIdAndParameterKeyAndIdempotencyKey(parameter.tenantId(), key, idempotency);
        if (existing.isPresent()) return result(parameter, existing.get());
        if (request == null || request.baseVersion() == null || request.baseVersion() != parameter.version()) {
            throw SummerException.withMessage(SystemParameterErrorCode.VERSION_CONFLICT, "The system parameter was changed by another request");
        }
        SystemParameterSnapshot snapshot = merge(parameter, request);
        validate(snapshot);
        recordInitial(parameter, authentication);
        SystemParameterVersionEntity saved = versions.saveAndFlush(new SystemParameterVersionEntity(parameter.tenantId(), key,
                nextRevision(parameter.tenantId(), key), SystemParameterReleaseState.DRAFT, serialize(snapshot), parameter.version(),
                idempotency, principal(authentication)));
        audit.recordTenantResourceSuccess(resolve(authentication), "system-parameter:" + key, "SYSTEM_PARAMETER_DRAFT_SAVED", requestId, authentication);
        return result(parameter, saved);
    }

    @Transactional
    SystemParameterReleaseResult publish(String key, SystemParameterReleaseCommand command, String requestId,
                                         Authentication authentication) {
        SystemParameterEntity parameter = current(key, authentication);
        String idempotency = idempotency(command == null ? null : command.idempotencyKey());
        var existing = versions.findByTenantIdAndParameterKeyAndIdempotencyKey(parameter.tenantId(), key, idempotency);
        if (existing.isPresent()) return result(parameter, existing.get());
        checkExpected(parameter, command);
        SystemParameterVersionEntity draft = version(parameter.tenantId(), key, command.revision());
        if (draft.state() != SystemParameterReleaseState.DRAFT) throw SummerException.withMessage(SystemParameterErrorCode.VERSION_CONFLICT, "Only a draft can be published");
        SystemParameterSnapshot snapshot = readSnapshot(draft.snapshotJson());
        validate(snapshot);
        return apply(parameter, snapshot, SystemParameterReleaseState.PUBLISHED, "SYSTEM_PARAMETER_PUBLISHED",
                requestId, authentication, idempotency);
    }

    @Transactional
    SystemParameterReleaseResult rollback(String key, SystemParameterReleaseCommand command, String requestId,
                                          Authentication authentication) {
        SystemParameterEntity parameter = current(key, authentication);
        String idempotency = idempotency(command == null ? null : command.idempotencyKey());
        var existing = versions.findByTenantIdAndParameterKeyAndIdempotencyKey(parameter.tenantId(), key, idempotency);
        if (existing.isPresent()) return result(parameter, existing.get());
        checkExpected(parameter, command);
        SystemParameterVersionEntity target = version(parameter.tenantId(), key, command.revision());
        if (target.state() == SystemParameterReleaseState.DRAFT) throw SummerException.withMessage(SystemParameterErrorCode.VERSION_CONFLICT, "Draft versions cannot be rolled back to");
        SystemParameterSnapshot snapshot = readSnapshot(target.snapshotJson());
        validate(snapshot);
        return apply(parameter, snapshot, SystemParameterReleaseState.ROLLED_BACK, "SYSTEM_PARAMETER_ROLLED_BACK",
                requestId, authentication, idempotency);
    }

    @Transactional
    SystemParameterReleaseResult deactivate(String key, SystemParameterReleaseCommand command, String requestId,
                                            Authentication authentication) {
        SystemParameterEntity parameter = current(key, authentication);
        String idempotency = idempotency(command == null ? null : command.idempotencyKey());
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
    SystemParameterVersionPage versions(String key, Authentication authentication) {
        SystemParameterEntity parameter = current(key, authentication);
        List<SystemParameterVersionEntity> rows = versions.findAllByTenantIdAndParameterKeyOrderByRevisionAsc(parameter.tenantId(), key);
        SystemParameterReleaseState currentState = parameter.status() == SystemParameterStatus.INACTIVE
                ? SystemParameterReleaseState.INACTIVE : SystemParameterReleaseState.PUBLISHED;
        return new SystemParameterVersionPage(rows.stream().map(row -> view(row, parameter.version())).toList(),
                parameter.version(), currentState);
    }

    private SystemParameterReleaseResult apply(SystemParameterEntity parameter, SystemParameterSnapshot snapshot,
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
            throw SummerException.withMessage(SystemParameterErrorCode.VERSION_CONFLICT, "The system parameter was changed by another request", exception);
        }
    }

    private SystemParameterEntity current(String key, Authentication authentication) {
        return parameters.findScoped(key, dataScopes.filter(resolve(authentication), "system-parameters", authentication))
                .orElseThrow(() -> SummerException.withMessage(CommonErrorCode.NOT_FOUND, "System parameter not found: " + key));
    }

    private SystemParameterVersionEntity version(String tenant, String key, Long revision) {
        if (revision == null || revision < 0) throw invalidRelease("revision is required");
        return versions.findByTenantIdAndParameterKeyAndRevision(tenant, key, revision)
                .orElseThrow(() -> SummerException.withMessage(CommonErrorCode.NOT_FOUND, "System parameter version not found: " + key + ":" + revision));
    }

    private void checkExpected(SystemParameterEntity parameter, SystemParameterReleaseCommand command) {
        if (command == null || command.expectedVersion() == null || command.expectedVersion() != parameter.version()) {
            throw SummerException.withMessage(SystemParameterErrorCode.VERSION_CONFLICT, "The system parameter was changed by another request");
        }
    }

    private long nextRevision(String tenant, String key) {
        return versions.findTopByTenantIdAndParameterKeyOrderByRevisionDesc(tenant, key)
                .map(value -> value.revision() + 1).orElse(0L);
    }

    private SystemParameterSnapshot merge(SystemParameterEntity parameter, SystemParameterDraftRequest request) {
        if (request == null) throw invalidRelease("request is required");
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

    private void validate(SystemParameterSnapshot value) {
        List<SummerException.Violation> violations = new ArrayList<>();
        if (value.required() && value.value() == null) violations.add(new SummerException.Violation("value", "is required"));
        if (value.min() != null && value.max() != null && value.min().compareTo(value.max()) > 0) {
            violations.add(new SummerException.Violation("min", "must not exceed max"));
        }
        if (value.value() != null) {
            try {
                switch (value.type()) {
                    case STRING -> {
                        if (!(value.value() instanceof String text)) violations.add(new SummerException.Violation("value", "must be a string"));
                        else {
                            if (value.pattern() != null && !Pattern.compile(value.pattern()).matcher(text).matches()) violations.add(new SummerException.Violation("value", "does not match pattern"));
                            if (value.min() != null && BigDecimal.valueOf(text.length()).compareTo(value.min()) < 0) violations.add(new SummerException.Violation("value", "is shorter than min"));
                            if (value.max() != null && BigDecimal.valueOf(text.length()).compareTo(value.max()) > 0) violations.add(new SummerException.Violation("value", "is longer than max"));
                        }
                    }
                    case INTEGER, DECIMAL -> {
                        if (!(value.value() instanceof Number)) violations.add(new SummerException.Violation("value", "must be numeric"));
                        else {
                            BigDecimal number = new BigDecimal(value.value().toString());
                            if (value.type() == SystemParameterType.INTEGER && number.stripTrailingZeros().scale() > 0) violations.add(new SummerException.Violation("value", "must be an integer"));
                            if (value.min() != null && number.compareTo(value.min()) < 0) violations.add(new SummerException.Violation("value", "is below min"));
                            if (value.max() != null && number.compareTo(value.max()) > 0) violations.add(new SummerException.Violation("value", "is above max"));
                        }
                    }
                    case BOOLEAN -> { if (!(value.value() instanceof Boolean)) violations.add(new SummerException.Violation("value", "must be true or false")); }
                }
            } catch (PatternSyntaxException exception) {
                violations.add(new SummerException.Violation("pattern", "must be a valid regular expression"));
            }
        }
        if (value.type() != SystemParameterType.STRING && value.pattern() != null) violations.add(new SummerException.Violation("pattern", "is only supported for string parameters"));
        if (!violations.isEmpty()) throw new FieldValidationException(SystemParameterErrorCode.INVALID, violations, "System parameter release validation failed", null);
    }

    private SystemParameterReleaseResult result(SystemParameterEntity parameter, SystemParameterVersionEntity version) {
        return new SystemParameterReleaseResult(new SystemParameterView(parameter.id(), parameter.key(), parameter.type(),
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

    private static String idempotency(String value) {
        if (value == null || value.isBlank() || value.length() > 120) throw invalidRelease("idempotencyKey is required");
        return value;
    }

    private static FieldValidationException invalidRelease(String message) {
        return new FieldValidationException(SystemParameterErrorCode.INVALID,
                List.of(new SummerException.Violation("request", message)), message, null);
    }

    private static String principal(Authentication authentication) { return authentication == null ? "system" : authentication.getName(); }
    private static String blank(String value) { return value == null || value.isBlank() ? null : value; }
}



