package com.abluepoint.summer.sample.systemparameter.domain;
import com.abluepoint.summer.common.error.api.SummerException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
public final class SystemParameterRules {
    private SystemParameterRules() {}
    public static List<SummerException.Violation> validate(String key, SystemParameterType type, Object value,
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
    public static void validateSnapshot(SystemParameterSnapshot value) {
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
        if (!violations.isEmpty()) throw new SystemParameterReleaseValidationException(violations);
    }
    public static String idempotency(String value) {
        if (value == null || value.isBlank() || value.length() > 120) throw new SystemParameterReleaseValidationException("idempotencyKey is required");
        return value;
    }
    public static void expected(Long expected,long current) { if(expected==null||expected!=current) throw new SystemParameterVersionConflictException(); }
    public static void publish(SystemParameterReleaseState state) { if(state!=SystemParameterReleaseState.DRAFT) throw new SystemParameterReleaseConflictException("Only a draft can be published"); }
    public static void rollback(SystemParameterReleaseState state) { if(state==SystemParameterReleaseState.DRAFT) throw new SystemParameterReleaseConflictException("Draft versions cannot be rolled back to"); }
    public static boolean deadLetter(int attempts) { return attempts+1>=3; }
    public static long retrySeconds(int attempts) { return 5L << attempts; }
    public static String safeError(String error) { if(error==null||error.isBlank()) return "delivery_failed"; return error.length()<=500?error:error.substring(0,500); }

}
