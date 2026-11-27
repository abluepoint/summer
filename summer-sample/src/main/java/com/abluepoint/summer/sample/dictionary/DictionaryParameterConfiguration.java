package com.abluepoint.summer.sample.dictionary;

import com.abluepoint.summer.mvc.validate.AbstractStyle;
import com.abluepoint.summer.mvc.validate.Style;
import com.abluepoint.summer.mvc.validate.Styles;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;
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

    @Bean
    Style positiveRevisionStyle() {
        return Styles.longValue().min(1).build();
    }

    @Bean
    Style divisionLevelStyle() {
        return Styles.integer().range(1, 4).build();
    }

    @Bean
    Style divisionCodeStyle() {
        return Styles.string().matches(Pattern.compile("[0-9]{6}(?:[0-9]{3})?"), "a 6- or 9-digit code").build();
    }

    @Bean
    Style divisionIdStyle() {
        return Styles.string().minLength(1).maxLength(36).build();
    }

    @Bean
    Style divisionNameStyle() {
        return new AbstractStyle(String.class, "1 to 120 characters after stripping whitespace", Map.of()) {
            @Override
            public boolean validate(String value) {
                return validDivisionName(value);
            }

            @Override
            public Object getTypedObject(String value) {
                return value.strip();
            }
        };
    }

    static boolean validDivisionName(String value) {
        return value != null && !value.isBlank() && value.strip().length() <= 120;
    }
}
