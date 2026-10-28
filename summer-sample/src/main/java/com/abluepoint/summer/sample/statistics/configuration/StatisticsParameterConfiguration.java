package com.abluepoint.summer.sample.statistics.configuration;

@org.springframework.context.annotation.Configuration(proxyBeanMethods = false)
class StatisticsParameterConfiguration {
    @org.springframework.context.annotation.Bean
    static com.abluepoint.summer.mvc.parameter.spi.Style statisticsIdentifierStyle() {
        return com.abluepoint.summer.mvc.parameter.api.Styles.string().matches(java.util.regex.Pattern.compile("[A-Za-z0-9_-]{1,80}"), "a valid enterprise or organization id").build();
    }

    @org.springframework.context.annotation.Bean
    static com.abluepoint.summer.mvc.parameter.spi.Style statisticsInstantStyle() {
        return com.abluepoint.summer.mvc.parameter.api.Styles.string().matches(java.util.regex.Pattern.compile(".{1,64}"), "an ISO-8601 instant").build();
    }

}
