package com.abluepoint.summer.sample.dictionary.api;

import com.abluepoint.summer.mvc.parameter.spi.Style;
import com.abluepoint.summer.mvc.parameter.api.Styles;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.regex.Pattern;

@Configuration(proxyBeanMethods = false)
public class DictionaryParameterConfiguration {
    @Bean
    public Style dictionaryCodeStyle() {
        return Styles.string().matches(Pattern.compile("[a-z][a-z0-9-]{1,78}[a-z0-9]"), "a valid dictionary code").build();
    }

    @Bean
    public Style dictionaryNameStyle() {
        return Styles.string().nonBlank().maxLength(200).build();
    }

    @Bean
    public Style dictionaryBooleanStyle() {
        return Styles.booleanValue().build();
    }
}
