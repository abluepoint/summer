package com.abluepoint.summer.mvc.validate;

import com.abluepoint.summer.common.error.CommonErrorCode;
import com.abluepoint.summer.common.error.SummerException;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FieldValidationExceptionTest {
    @Test
    void publicFieldCreationPreservesCauseAndSnapshotsItsSharedInformation() {
        var original = new SummerException.Violation("value", "must be numeric");
        var details = new ArrayList<>(List.of(original));
        Object[] arguments = {"age"};
        var cause = new IllegalArgumentException("internal cause");
        SummerException failure = new FieldValidationException(
                CommonErrorCode.INVALID_ARGUMENT, details, "literal {0}", cause, arguments);

        details.clear();
        arguments[0] = "changed";
        failure.messageArguments()[0] = "changed again";

        assertThat(failure.errorCode()).isSameAs(CommonErrorCode.INVALID_ARGUMENT);
        assertThat(failure.getCause()).isSameAs(cause);
        assertThat(failure.getMessage()).isEqualTo("literal {0}");
        assertThat(failure.explicitMessage()).isEqualTo("literal {0}");
        assertThat(failure.messageArguments()).containsExactly("age");
        assertThat(failure.violations()).containsExactly(original);
        assertThatThrownBy(() -> failure.violations().clear()).isInstanceOf(UnsupportedOperationException.class);
    }
}
