package com.abluepoint.summer.sample.dictionary;

import com.abluepoint.summer.mvc.validate.Style;
import com.abluepoint.summer.mvc.validate.Styles;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.regex.Pattern;

@Configuration(proxyBeanMethods = false)
class DictionaryParameterConfiguration {
    @Bean
    Style dictionaryCodeStyle() {
        return Styles.string().matches(Pattern.compile("[a-z][a-z0-9-]{1,78}[a-z0-9]"), "a valid dictionary code").build();
    }

    @Bean
    Style dictionaryNameStyle() {
        return Styles.string().nonBlank().maxLength(200).build();
    }

    @Bean
    Style dictionaryBooleanStyle() {
        return Styles.booleanValue().build();
    }
}
