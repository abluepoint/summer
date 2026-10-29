package com.abluepoint.summer.sample.systemparameter.api;

import com.abluepoint.summer.mvc.parameter.spi.Style;
import com.abluepoint.summer.mvc.parameter.api.Styles;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class SystemParameterParameterConfiguration {
    @Bean
    public Style systemParameterPageStyle() {
        return Styles.integer().matches(java.util.regex.Pattern.compile("0|[1-9]\\d{0,8}"), "a non-negative page number").build();
    }

    @Bean
    public Style systemParameterPageSizeStyle() {
        return Styles.integer().range(1, 100).build();
    }

    @Bean
    public Style systemParameterKeyStyle() {
        return Styles.string().matches(java.util.regex.Pattern.compile("[a-z][a-z0-9._-]{2,119}"), "a valid system parameter key").build();
    }

    @Bean
    public Style systemParameterTypeStyle() {
        return Styles.string().oneOf("STRING", "INTEGER", "DECIMAL", "BOOLEAN").build();
    }

    @Bean
    public Style systemParameterStatusStyle() {
        return Styles.string().oneOf("ACTIVE", "INACTIVE").build();
    }

    @Bean
    public Style systemParameterSortStyle() {
        return Styles.string().oneOf("key", "type", "status", "createdAt", "updatedAt", "version").build();
    }

    @Bean
    public Style systemParameterSortDirectionStyle() {
        return Styles.string().oneOf("asc", "desc").build();
    }

    @Bean
    public Style systemParameterProjectionStyle() {
        return Styles.string().oneOf("summary", "detail").build();
    }

    @Bean
    public Style systemParameterTenantStyle() {
        return Styles.string().matches(java.util.regex.Pattern.compile("[a-z][a-z0-9-]{2,79}"), "a valid tenant id").build();
    }
}
