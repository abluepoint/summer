package com.abluepoint.summer.mvc.parameter.api;

import com.abluepoint.summer.mvc.parameter.spi.AbstractStyle;
import com.abluepoint.summer.mvc.parameter.spi.Style;

import org.springframework.core.convert.ConversionService;
import org.springframework.core.convert.support.DefaultConversionService;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * Fluent factories for application-owned parameter styles.
 *
 * <p>The factories create a {@link Style} value; they do not register a
 * name. Applications register the value with a normal Spring {@code @Bean},
 * and the bean method name becomes the name referenced by {@code @Field}.</p>
 */
public final class Styles {

    private Styles() {
    }

    public static StringBuilder string() {
        return new StringBuilder(String.class, "a string");
    }

    public static IntegerBuilder integer() {
        return new IntegerBuilder();
    }

    public static LongBuilder longValue() {
        return new LongBuilder();
    }

    /** Alias for callers that prefer a type-oriented name. */
    public static LongBuilder longNumber() {
        return longValue();
    }

    public static DecimalBuilder decimal() {
        return new DecimalBuilder();
    }

    public static BooleanBuilder booleanValue() {
        return new BooleanBuilder();
    }

    /** Alias for callers that prefer the shorter spelling. */
    public static BooleanBuilder bool() {
        return booleanValue();
    }

    public static final class StringBuilder extends Builder<String, StringBuilder> {

        private StringBuilder(Class<String> type, String description) {
            super(type, description);
        }

        public StringBuilder email() {
            return rule(
                    value -> value.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+"),
                    "a valid email address"
            );
        }

        public StringBuilder nonBlank() {
            return rule(value -> !value.trim().isEmpty(), "non-blank text");
        }
    }

    public static final class IntegerBuilder extends Builder<Integer, IntegerBuilder> {

        private IntegerBuilder() {
            super(Integer.class, "an integer");
            pattern("-?(0|[1-9]\\d*)", "an integer");
        }

        public IntegerBuilder min(int minimum) {
            return rule(value -> Integer.parseInt(value) >= minimum, "an integer at least " + minimum);
        }

        public IntegerBuilder max(int maximum) {
            return rule(value -> Integer.parseInt(value) <= maximum, "an integer at most " + maximum);
        }

        public IntegerBuilder range(int minimum, int maximum) {
            if (minimum > maximum) {
                throw new IllegalArgumentException("minimum must not exceed maximum");
            }
            return min(minimum).max(maximum);
        }
    }

    public static final class LongBuilder extends Builder<Long, LongBuilder> {

        private LongBuilder() {
            super(Long.class, "a long integer");
            pattern("-?(0|[1-9]\\d*)", "a long integer");
        }

        public LongBuilder min(long minimum) {
            return rule(value -> Long.parseLong(value) >= minimum, "a long integer at least " + minimum);
        }

        public LongBuilder max(long maximum) {
            return rule(value -> Long.parseLong(value) <= maximum, "a long integer at most " + maximum);
        }

        public LongBuilder range(long minimum, long maximum) {
            if (minimum > maximum) {
                throw new IllegalArgumentException("minimum must not exceed maximum");
            }
            return min(minimum).max(maximum);
        }
    }

    public static final class DecimalBuilder extends Builder<BigDecimal, DecimalBuilder> {

        private DecimalBuilder() {
            super(BigDecimal.class, "a decimal number");
            pattern("-?(0|[1-9]\\d*)(\\.\\d+)?", "a decimal number");
        }

        public DecimalBuilder min(BigDecimal minimum) {
            Objects.requireNonNull(minimum, "minimum");
            return rule(value -> new BigDecimal(value).compareTo(minimum) >= 0,
                    "a decimal number at least " + minimum);
        }

        public DecimalBuilder max(BigDecimal maximum) {
            Objects.requireNonNull(maximum, "maximum");
            return rule(value -> new BigDecimal(value).compareTo(maximum) <= 0,
                    "a decimal number at most " + maximum);
        }
    }

    public static final class BooleanBuilder extends Builder<Boolean, BooleanBuilder> {

        private BooleanBuilder() {
            super(Boolean.class, "true or false");
            rule(value -> "true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value),
                    "true or false");
        }
    }

    public abstract static class Builder<T, SELF extends Builder<T, SELF>> {

        private static final ConversionService CONVERSION_SERVICE = DefaultConversionService.getSharedInstance();

        private final Class<T> type;
        private final List<Rule> rules = new ArrayList<>();
        private final List<String> ruleDescriptions = new ArrayList<>();
        private final String initialDescription;
        private String explicitDescription;

        protected Builder(Class<T> type, String description) {
            this.type = Objects.requireNonNull(type, "type");
            this.initialDescription = Objects.requireNonNull(description, "description");
        }

        @SuppressWarnings("unchecked")
        protected SELF self() {
            return (SELF) this;
        }

        protected SELF rule(Predicate<String> predicate, String ruleDescription) {
            rules.add(new Rule(Objects.requireNonNull(predicate, "predicate"),
                    Objects.requireNonNull(ruleDescription, "ruleDescription")));
            ruleDescriptions.add(ruleDescription);
            return self();
        }

        protected SELF pattern(String expression, String ruleDescription) {
            Pattern pattern = Pattern.compile(expression);
            return rule(value -> pattern.matcher(value).matches(), ruleDescription);
        }

        public SELF description(String value) {
            explicitDescription = Objects.requireNonNull(value, "description");
            return self();
        }

        public SELF matches(Pattern pattern, String ruleDescription) {
            Objects.requireNonNull(pattern, "pattern");
            return rule(value -> pattern.matcher(value).matches(), ruleDescription);
        }

        public SELF oneOf(String... values) {
            Objects.requireNonNull(values, "values");
            if (values.length == 0) {
                throw new IllegalArgumentException("values must not be empty");
            }
            List<String> allowed = List.of(values);
            return rule(allowed::contains, "one of " + String.join(", ", allowed));
        }

        public SELF minLength(int minimum) {
            if (minimum < 0) {
                throw new IllegalArgumentException("minimum length must not be negative");
            }
            return rule(value -> value.length() >= minimum, "at least " + minimum + " characters");
        }

        public SELF maxLength(int maximum) {
            if (maximum < 0) {
                throw new IllegalArgumentException("maximum length must not be negative");
            }
            return rule(value -> value.length() <= maximum, "at most " + maximum + " characters");
        }

        public Style build() {
            String description = explicitDescription;
            if (description == null) {
                description = ruleDescriptions.isEmpty()
                        ? initialDescription
                        : String.join(" and ", ruleDescriptions.stream().distinct().toList());
            }
            return new BuiltStyle<>(type, description, List.copyOf(rules));
        }

        private record Rule(Predicate<String> predicate, String description) {
        }

        private static final class BuiltStyle<T> extends AbstractStyle {

            private final List<Rule> rules;

            private BuiltStyle(Class<T> type, String description, List<Rule> rules) {
                super(type, description, Map.of());
                this.rules = rules;
            }

            @Override
            public boolean validate(String value) {
                return value != null && rules.stream().allMatch(rule -> {
                    try {
                        return rule.predicate().test(value);
                    } catch (RuntimeException exception) {
                        return false;
                    }
                });
            }

            @Override
            public Object getTypedObject(String value) {
                return CONVERSION_SERVICE.convert(value, getParamType());
            }
        }
    }
}
