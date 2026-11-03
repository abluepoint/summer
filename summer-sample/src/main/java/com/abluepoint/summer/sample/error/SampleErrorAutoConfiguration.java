package com.abluepoint.summer.sample.error;

import com.abluepoint.summer.mvc.autoconfigure.SummerMvcAutoConfiguration;
import com.abluepoint.summer.mvc.error.DefaultErrorStatusResolver;
import com.abluepoint.summer.mvc.error.ErrorStatusResolver;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpStatus;

import java.util.Map;

import static com.abluepoint.summer.sample.error.SampleTransportErrorCode.*;

@AutoConfiguration(before = SummerMvcAutoConfiguration.class)
public class SampleErrorAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean(ErrorStatusResolver.class)
    ErrorStatusResolver sampleErrorStatusResolver() {
        return new DefaultErrorStatusResolver(Map.of(
                PRECONDITION_FAILED, HttpStatus.PRECONDITION_FAILED,
                OAUTH_CLIENT_VERSION_CONFLICT, HttpStatus.PRECONDITION_FAILED,
                BFF_UNAVAILABLE, HttpStatus.SERVICE_UNAVAILABLE,
                BFF_UPSTREAM_ERROR, HttpStatus.BAD_GATEWAY,
                SESSION_STORE_UNAVAILABLE, HttpStatus.SERVICE_UNAVAILABLE,
                DEPENDENCY_UNAVAILABLE, HttpStatus.SERVICE_UNAVAILABLE,
                DICTIONARY_SSE_LIMIT, HttpStatus.TOO_MANY_REQUESTS,
                TASK_SSE_LIMIT, HttpStatus.TOO_MANY_REQUESTS));
    }
}
