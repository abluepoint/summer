import com.abluepoint.summer.common.error.CommonErrorCode;
import com.abluepoint.summer.common.error.SummerException;
import java.util.List;

/** Compile against the pre-change Common API, then run unchanged against the candidate JAR. */
public final class LegacySummerExceptionClient {
    public static void main(String[] ignored) {
        Throwable cause = new IllegalArgumentException("client cause");
        SummerException arguments = new SummerException(CommonErrorCode.CONFLICT, new Object[]{"client argument"});
        SummerException caused = new SummerException(CommonErrorCode.CONFLICT, cause, new Object[]{"client argument"});
        SummerException fields = new SummerException(CommonErrorCode.INVALID_ARGUMENT,
                List.of(new SummerException.Violation("name", "is required")));
        if (arguments.errorCode() != CommonErrorCode.CONFLICT
                || !"client argument".equals(arguments.messageArguments()[0])
                || caused.getCause() != cause || fields.violations().size() != 1) {
            throw new IllegalStateException("Legacy Common constructor contract failed");
        }
    }
}
