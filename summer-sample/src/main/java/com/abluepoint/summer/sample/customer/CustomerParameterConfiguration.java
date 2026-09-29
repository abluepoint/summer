package com.abluepoint.summer.sample.customer;

import com.abluepoint.summer.mvc.validate.Style;
import com.abluepoint.summer.mvc.validate.Styles;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.regex.Pattern;

@Configuration(proxyBeanMethods = false)
class CustomerParameterConfiguration {

    @Bean("customerEmailStyle")
    Style customerEmailStyle() {
        return Styles.string()
                .email()
                .maxLength(200)
                .build();
    }

    @Bean("customerDisplayNameStyle")
    Style customerDisplayNameStyle() {
        return Styles.string()
                .nonBlank()
                .maxLength(200)
                .build();
    }

    @Bean("customerPageStyle")
    Style customerPageStyle() {
        return Styles.integer()
                .matches(Pattern.compile("0|[1-9]\\d{0,8}"), "a non-negative page number")
                .build();
    }

    @Bean("customerPageSizeStyle")
    Style customerPageSizeStyle() {
        return Styles.integer()
                .range(1, 100)
                .build();
    }

    @Bean("customerSortStyle")
    Style customerSortStyle() {
        return Styles.string().oneOf("email", "displayName", "active").build();
    }

    @Bean("customerSortDirectionStyle")
    Style customerSortDirectionStyle() {
        return Styles.string().oneOf("asc", "desc").build();
    }

    @Bean("customerProjectionStyle")
    Style customerProjectionStyle() {
        return Styles.string().oneOf("summary", "detail").build();
    }

    @Bean("booleanStyle")
    Style booleanStyle() {
        return Styles.booleanValue().build();
    }
}
