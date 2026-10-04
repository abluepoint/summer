package com.abluepoint.summer.sample.systemparameter;

import com.abluepoint.summer.mvc.validate.Style;
import com.abluepoint.summer.mvc.validate.Styles;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class SystemParameterParameterConfiguration {
    @Bean
    Style systemParameterPageStyle() {
        return Styles.integer().matches(java.util.regex.Pattern.compile("0|[1-9]\\d{0,8}"), "a non-negative page number").build();
    }

    @Bean
    Style systemParameterPageSizeStyle() {
        return Styles.integer().range(1, 100).build();
    }

    @Bean
    Style systemParameterKeyStyle() {
        return Styles.string().matches(java.util.regex.Pattern.compile("[a-z][a-z0-9._-]{2,119}"), "a valid system parameter key").build();
    }

    @Bean
    Style systemParameterTypeStyle() {
        return Styles.string().oneOf("STRING", "INTEGER", "DECIMAL", "BOOLEAN").build();
    }

    @Bean
    Style systemParameterStatusStyle() {
        return Styles.string().oneOf("ACTIVE", "INACTIVE").build();
    }
}
