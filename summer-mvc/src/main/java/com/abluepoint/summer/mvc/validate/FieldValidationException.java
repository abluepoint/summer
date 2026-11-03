package com.abluepoint.summer.mvc.validate;

import com.abluepoint.summer.common.error.CommonErrorCode;
import com.abluepoint.summer.common.error.ErrorCode;
import com.abluepoint.summer.common.error.SummerException;

import java.util.List;

/** A field-level failure using the shared error information and violation model. */
public final class FieldValidationException extends SummerException {

    public FieldValidationException(List<SummerException.Violation> violations) {
        this(CommonErrorCode.INVALID_ARGUMENT, violations);
    }

    public FieldValidationException(ErrorCode errorCode, List<SummerException.Violation> violations) {
        this(errorCode, violations, null, null);
    }

    /** A null message retains error-code resolution; non-null messages are literal. */
    public FieldValidationException(
            ErrorCode errorCode, List<SummerException.Violation> violations,
            String message, Throwable cause, Object... messageArguments
    ) {
        super(errorCode, message, cause, messageArguments, violations);
    }
}
