package com.abluepoint.summer.sample.authorization;

import com.abluepoint.summer.common.error.SummerException;
import com.jayway.jsonpath.JsonPath;
import org.springframework.test.web.servlet.ResultMatcher;

import static org.junit.jupiter.api.Assertions.*;

/** Assertions at the full HTTP boundary for this work package's business failures. */
public final class UnifiedBusinessFailure {
    private UnifiedBusinessFailure() {}

    public static ResultMatcher unified(String code, String message) {
        return result -> {
            SummerException exception = assertInstanceOf(SummerException.class, result.getResolvedException());
            assertEquals(code, exception.errorCode().code());
            assertEquals(message, exception.explicitMessage());
            String body = result.getResponse().getContentAsString();
            assertEquals(code, JsonPath.read(body, "$.error.code"));
            assertEquals(message, JsonPath.read(body, "$.error.message"));
            assertEquals(result.getResponse().getHeader("X-Request-Id"), JsonPath.read(body, "$.requestId"));
            assertFalse(result.getResponse().getHeader("X-Request-Id").isBlank());
        };
    }
}
