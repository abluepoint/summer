package com.abluepoint.summer.sample.systemparameter;

import com.abluepoint.summer.common.error.SummerException;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

@Service
class SystemParameterService {
    static final String TENANT_ID = "ent-acme";
    private static final Pattern KEY = Pattern.compile("[a-z][a-z0-9._-]{2,119}");

    private final SystemParameterRepository repository;
    private final ObjectMapper objectMapper;
    private final CustomerAuditService audit;
    private final SystemParameterEventService events;
    private final VersionedTwoLevelCache cache;

    SystemParameterService(SystemParameterRepository repository, ObjectMapper objectMapper,
                           CustomerAuditService audit, SystemParameterEventService events,
                           VersionedTwoLevelCache cache) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.audit = audit;
        this.events = events;
        this.cache = cache;
    }

    @Transactional
    SystemParameterView create(CreateSystemParameterRequest request, String requestId,
                               Authentication authentication) {
        List<SummerException.Violation> violations = validateCreate(request);
        if (!violations.isEmpty()) throw new SystemParameterValidationException(violations);
        if (repository.existsByKey(request.key())) {
            throw new SystemParameterConflictException("System parameter key is already in use");
        }
        String valueJson = serialize(request.value());
        try {
            SystemParameterEntity saved = repository.saveAndFlush(new SystemParameterEntity(
                    request.key(), request.type(), valueJson, Boolean.TRUE.equals(request.required()),
                    request.min(), request.max(), blankToNull(request.pattern()),
                    Boolean.TRUE.equals(request.publicValue()), blankToNull(request.description())));
            events.enqueue(saved, "SYSTEM_PARAMETER_CHANGED", requestId);
            cache.evictLocal(TENANT_ID, "system-parameter");
            audit.recordResourceSuccess("system-parameter:" + saved.key(), "SYSTEM_PARAMETER_CREATED", requestId, authentication);
            return view(saved);
        } catch (DataIntegrityViolationException exception) {
            throw new SystemParameterConflictException("System parameter key is already in use");
        }
    }

    @Transactional(readOnly = true)
    SystemParameterPage list(int page, int size, SystemParameterType type, SystemParameterStatus status) {
        List<SystemParameterView> filtered = repository.findAllByTenantIdOrderByKeyAsc(TENANT_ID).stream()
                .filter(value -> type == null || value.type() == type)
                .filter(value -> status == null || value.status() == status)
                .map(this::view)
                .toList();
        int safePage = Math.max(0, page);
        int safeSize = Math.max(1, Math.min(size, 200));
        int from = Math.min(safePage * safeSize, filtered.size());
        int to = Math.min(from + safeSize, filtered.size());
        int totalPages = (filtered.size() + safeSize - 1) / safeSize;
        return new SystemParameterPage(filtered.subList(from, to), safePage, safeSize,
                filtered.size(), totalPages);
    }

    @Transactional(readOnly = true)
    SystemParameterView find(String key) {
        return cache.get(TENANT_ID, "system-parameter", key, SystemParameterView.class,
                () -> repository.findByKey(key).filter(value -> TENANT_ID.equals(value.tenantId()))
                        .map(this::view).orElseThrow(() -> new SystemParameterNotFoundException(key)));
    }

    @Transactional
    SystemParameterView update(String key, UpdateSystemParameterRequest request, String requestId,
                               Authentication authentication) {
        SystemParameterEntity entity = repository.findByKey(key)
                .filter(value -> TENANT_ID.equals(value.tenantId()))
                .orElseThrow(() -> new SystemParameterNotFoundException(key));
        if (request.version() == null || request.version() != entity.version()) {
            throw new SystemParameterVersionConflictException();
        }
        Object value = request.value() == null ? deserialize(entity.valueJson()) : request.value();
        boolean required = request.required() == null ? entity.required() : request.required();
        BigDecimal min = request.min() == null ? entity.min() : request.min();
        BigDecimal max = request.max() == null ? entity.max() : request.max();
        String pattern = request.pattern() == null ? entity.pattern() : blankToNull(request.pattern());
        boolean publicValue = request.publicValue() == null ? entity.publicValue() : request.publicValue();
        String description = request.description() == null ? entity.description() : blankToNull(request.description());
        SystemParameterStatus status = request.status() == null ? entity.status() : request.status();
        List<SummerException.Violation> violations = validate(entity.key(), entity.type(), value, required,
                min, max, pattern);
        if (!violations.isEmpty()) throw new SystemParameterValidationException(violations);
        entity.update(serialize(value), required, min, max, pattern, publicValue, description, status);
        try {
            SystemParameterEntity saved = repository.saveAndFlush(entity);
            events.enqueue(saved, "SYSTEM_PARAMETER_CHANGED", requestId);
            cache.evictLocal(TENANT_ID, "system-parameter");
            audit.recordResourceSuccess("system-parameter:" + saved.key(), "SYSTEM_PARAMETER_UPDATED", requestId, authentication);
            return view(saved);
        } catch (ObjectOptimisticLockingFailureException exception) {
            throw new SystemParameterVersionConflictException();
        }
    }

    @Transactional
    SystemParameterView setStatus(String key, SystemParameterStatus status, String requestId,
                                  Authentication authentication) {
        SystemParameterEntity entity = repository.findByKey(key)
                .filter(value -> TENANT_ID.equals(value.tenantId()))
                .orElseThrow(() -> new SystemParameterNotFoundException(key));
        entity.update(entity.valueJson(), entity.required(), entity.min(), entity.max(), entity.pattern(),
                entity.publicValue(), entity.description(), status);
        try {
            SystemParameterEntity saved = repository.saveAndFlush(entity);
            events.enqueue(saved, "SYSTEM_PARAMETER_CHANGED", requestId);
            cache.evictLocal(TENANT_ID, "system-parameter");
            audit.recordResourceSuccess("system-parameter:" + saved.key(),
                    status == SystemParameterStatus.ACTIVE ? "SYSTEM_PARAMETER_ENABLED" : "SYSTEM_PARAMETER_DISABLED",
                    requestId, authentication);
            return view(saved);
        } catch (ObjectOptimisticLockingFailureException exception) {
            throw new SystemParameterVersionConflictException();
        }
    }

    private List<SummerException.Violation> validateCreate(CreateSystemParameterRequest request) {
        List<SummerException.Violation> violations = new ArrayList<>();
        if (request == null) {
            violations.add(new SummerException.Violation("request", "is required"));
            return violations;
        }
        if (request.key() == null || !KEY.matcher(request.key()).matches()) {
            violations.add(new SummerException.Violation("key", "must be 3-120 lowercase characters, digits, dots, underscores or hyphens"));
        }
        if (request.type() == null) violations.add(new SummerException.Violation("type", "is required"));
        boolean required = request.required() == null || request.required();
        violations.addAll(validate(request.key(), request.type(), request.value(), required,
                request.min(), request.max(), blankToNull(request.pattern())));
        return violations;
    }

    private List<SummerException.Violation> validate(String key, SystemParameterType type, Object value,
                                                     boolean required, BigDecimal min, BigDecimal max,
                                                     String pattern) {
        List<SummerException.Violation> violations = new ArrayList<>();
        if (type == null) return violations;
        if (required && value == null) violations.add(new SummerException.Violation("value", "is required"));
        if (min != null && max != null && min.compareTo(max) > 0) {
            violations.add(new SummerException.Violation("min", "must not exceed max"));
        }
        if (value != null) {
            try {
                switch (type) {
                    case STRING -> {
                        if (!(value instanceof String text)) {
                            violations.add(new SummerException.Violation("value", "must be a string"));
                        } else {
                            if (pattern != null && !Pattern.compile(pattern).matcher(text).matches()) {
                                violations.add(new SummerException.Violation("value", "does not match pattern"));
                            }
                            if (min != null && BigDecimal.valueOf(text.length()).compareTo(min) < 0) {
                                violations.add(new SummerException.Violation("value", "is shorter than min"));
                            }
                            if (max != null && BigDecimal.valueOf(text.length()).compareTo(max) > 0) {
                                violations.add(new SummerException.Violation("value", "is longer than max"));
                            }
                        }
                    }
                    case INTEGER -> {
                        BigDecimal numeric = number(value, true);
                        if (numeric == null) violations.add(new SummerException.Violation("value", "must be an integer"));
                        else validateRange(numeric, min, max, violations);
                    }
                    case DECIMAL -> {
                        BigDecimal numeric = number(value, false);
                        if (numeric == null) violations.add(new SummerException.Violation("value", "must be a decimal number"));
                        else validateRange(numeric, min, max, violations);
                    }
                    case BOOLEAN -> {
                        if (!(value instanceof Boolean)) {
                            violations.add(new SummerException.Violation("value", "must be true or false"));
                        }
                    }
                }
            } catch (PatternSyntaxException exception) {
                violations.add(new SummerException.Violation("pattern", "must be a valid regular expression"));
            }
        }
        if (type != SystemParameterType.STRING && pattern != null) {
            violations.add(new SummerException.Violation("pattern", "is only supported for string parameters"));
        }
        return violations;
    }

    private static BigDecimal number(Object value, boolean integer) {
        if (!(value instanceof Number)) return null;
        BigDecimal numeric;
        try {
            numeric = new BigDecimal(value.toString());
        } catch (NumberFormatException exception) {
            return null;
        }
        return integer && numeric.stripTrailingZeros().scale() > 0 ? null : numeric;
    }

    private static void validateRange(BigDecimal numeric, BigDecimal min, BigDecimal max,
                                      List<SummerException.Violation> violations) {
        if (min != null && numeric.compareTo(min) < 0) violations.add(new SummerException.Violation("value", "is below min"));
        if (max != null && numeric.compareTo(max) > 0) violations.add(new SummerException.Violation("value", "is above max"));
    }

    private String serialize(Object value) {
        try {
            return value == null ? null : objectMapper.writeValueAsString(value);
        } catch (JacksonException exception) {
            throw new IllegalArgumentException("System parameter value cannot be serialized", exception);
        }
    }

    private Object deserialize(String value) {
        if (value == null) return null;
        try {
            return objectMapper.readValue(value, Object.class);
        } catch (JacksonException exception) {
            throw new IllegalStateException("System parameter value cannot be read", exception);
        }
    }

    private SystemParameterView view(SystemParameterEntity entity) {
        return new SystemParameterView(entity.id(), entity.key(), entity.type(), deserialize(entity.valueJson()),
                entity.required(), entity.min(), entity.max(), entity.pattern(), entity.publicValue(),
                entity.description(), entity.status(), entity.version(), entity.tenantId(),
                entity.createdAt(), entity.updatedAt());
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
