package com.abluepoint.summer.sample.customer;

import com.abluepoint.summer.mvc.validate.PatternStyle;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;
import java.util.regex.Pattern;

@Configuration(proxyBeanMethods = false)
class CustomerParameterConfiguration {

    @Bean("customerEmailStyle")
    PatternStyle customerEmailStyle() {
        return new PatternStyle(
                String.class,
                "a valid email address",
                Map.of(),
                Pattern.compile("(?=.{1,200}$)[^\\s@]+@[^\\s@]+\\.[^\\s@]+")
        );
    }

    @Bean("customerDisplayNameStyle")
    PatternStyle customerDisplayNameStyle() {
        return new PatternStyle(
                String.class,
                "a non-blank display name of at most 200 characters",
                Map.of(),
                Pattern.compile("(?=.{1,200}$).*\\S.*")
        );
    }

    @Bean("customerPageStyle")
    PatternStyle customerPageStyle() {
        return new PatternStyle(
                Integer.class,
                "a non-negative page number",
                Map.of(),
                Pattern.compile("0|[1-9]\\d{0,8}")
        );
    }

    @Bean("customerPageSizeStyle")
    PatternStyle customerPageSizeStyle() {
        return new PatternStyle(
                Integer.class,
                "a page size between 1 and 100",
                Map.of(),
                Pattern.compile("[1-9]|[1-9]\\d|100")
        );
    }

    @Bean("customerSortStyle")
    PatternStyle customerSortStyle() {
        return new PatternStyle(
                String.class,
                "one of email, displayName or active",
                Map.of(),
                Pattern.compile("email|displayName|active")
        );
    }

    @Bean("customerSortDirectionStyle")
    PatternStyle customerSortDirectionStyle() {
        return new PatternStyle(
                String.class,
                "one of asc or desc",
                Map.of(),
                Pattern.compile("asc|desc")
        );
    }

    @Bean("customerProjectionStyle")
    PatternStyle customerProjectionStyle() {
        return new PatternStyle(
                String.class,
                "one of summary or detail",
                Map.of(),
                Pattern.compile("summary|detail")
        );
    }
}
