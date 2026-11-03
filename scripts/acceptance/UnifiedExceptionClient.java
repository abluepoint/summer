import com.abluepoint.summer.common.error.CommonErrorCode;
import com.abluepoint.summer.common.error.SummerException;
import com.abluepoint.summer.mvc.validate.FieldValidationException;
import java.util.ArrayList;
import java.util.List;

/** Compile and execute using only the same candidate Common and MVC library JARs. */
public final class UnifiedExceptionClient {
    public static void main(String[] ignored) {
        Throwable cause = new IllegalArgumentException("client cause");
        Object[] arguments = {"original"};
        SummerException literal = SummerException.withMessage(CommonErrorCode.CONFLICT,
                "literal {0}", cause, arguments);
        arguments[0] = "mutated";
        Object[] returned = literal.messageArguments();
        returned[0] = "mutated again";
        List<SummerException.Violation> violations = new ArrayList<>();
        violations.add(new SummerException.Violation("name", "is required"));
        FieldValidationException field = new FieldValidationException(CommonErrorCode.INVALID_ARGUMENT,
                violations, "field failure", cause);
        violations.clear();
        if (!"literal {0}".equals(literal.getMessage()) || literal.getCause() != cause
                || !"original".equals(literal.messageArguments()[0])
                || !"".equals(SummerException.withMessage(CommonErrorCode.CONFLICT, "").explicitMessage())
                || SummerException.withMessage(CommonErrorCode.CONFLICT, null).explicitMessage() != null
                || field.getCause() != cause || field.violations().size() != 1
                || !(field instanceof SummerException)) {
            throw new IllegalStateException("Candidate unified exception client contract failed");
        }
    }
}
