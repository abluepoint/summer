package com.abluepoint.summer.sample.customer.configuration;

import com.abluepoint.summer.mvc.parameter.spi.Style;
import com.abluepoint.summer.mvc.parameter.api.Styles;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.regex.Pattern;

@Configuration(proxyBeanMethods = false)
class CustomerParameterConfiguration {

    @Bean("customerUsernameStyle")
    Style customerUsernameStyle() {
        return Styles.string().matches(Pattern.compile("[a-z0-9][a-z0-9._-]{1,62}[a-z0-9]"), "3-64 lowercase letters, digits, dot, underscore or hyphen; begin and end with a letter or digit").build();
    }

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
        return Styles.string().oneOf("username", "email", "displayName", "active").build();
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
