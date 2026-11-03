package com.abluepoint.summer.common.error;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class SummerExceptionTest {
    @Test
    void explicitMessageAndCauseCoexistWithoutChangingLegacyStringArguments() {
        var cause = new IllegalStateException("internal diagnostic");
        var legacy = new SummerException(CommonErrorCode.NOT_FOUND, "template argument");
        assertThat(legacy.messageArguments()).containsExactly("template argument");
        assertThat(legacy.getMessage()).isEqualTo(CommonErrorCode.NOT_FOUND.defaultMessage());
        var exception = SummerException.withMessage(CommonErrorCode.NOT_FOUND, "  literal {0}  ", cause);
        assertThat(exception.getMessage()).isEqualTo("  literal {0}  ");
        assertThat(exception.explicitMessage()).isEqualTo("  literal {0}  ");
        assertThat(exception.getCause()).isSameAs(cause);
    }
    @Test
    void legacyConstructorsAndFieldSubclassesKeepIndependentSnapshots() {
        var cause = new IllegalStateException("cause");
        Object[] arguments = {"first", 7};
        var legacy = new SummerException(CommonErrorCode.NOT_FOUND, cause, arguments);
        arguments[0] = "changed";
        legacy.messageArguments()[1] = 99;
        assertThat(legacy.messageArguments()).containsExactly("first", 7);
        assertThat(legacy.getCause()).isSameAs(cause);
        var violations = new java.util.ArrayList<SummerException.Violation>();
        violations.add(new SummerException.Violation("customer.name", "required"));
        var oldFields = new SummerException(CommonErrorCode.INVALID_ARGUMENT, violations);
        var fields = new FieldFailure(CommonErrorCode.CONFLICT, "field message", cause, violations);
        violations.clear();
        assertThat(oldFields.violations()).containsExactly(new SummerException.Violation("customer.name", "required"));
        assertThat(oldFields.messageArguments()).isEmpty();
        assertThat(fields.errorCode()).isSameAs(CommonErrorCode.CONFLICT);
        assertThat(fields.getMessage()).isEqualTo("field message");
        assertThat(fields.getCause()).isSameAs(cause);
        assertThat(fields.violations()).containsExactly(new SummerException.Violation("customer.name", "required"));
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> fields.violations().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void messageAbsenceAndMandatoryCodeAreExplicit() {
        for (String message : new String[]{"", "   ", "{0}"}) {
            assertThat(SummerException.withMessage(CommonErrorCode.NOT_FOUND, message).getMessage()).isEqualTo(message);
        }
        var absent = SummerException.withMessage(CommonErrorCode.NOT_FOUND, null, "argument");
        assertThat(absent.explicitMessage()).isNull();
        assertThat(absent.getMessage()).isEqualTo(CommonErrorCode.NOT_FOUND.defaultMessage());
        assertThat(absent.messageArguments()).containsExactly("argument");
        org.assertj.core.api.Assertions.assertThatNullPointerException()
                .isThrownBy(() -> SummerException.withMessage(null, "explicit"));
    }

    private static class FieldFailure extends SummerException {
        FieldFailure(ErrorCode code, String message, Throwable cause, java.util.List<Violation> violations) {
            super(code, message, cause, new Object[0], violations);
        }
    }
}
