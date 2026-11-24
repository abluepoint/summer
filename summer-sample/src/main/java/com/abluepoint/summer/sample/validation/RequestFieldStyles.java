package com.abluepoint.summer.sample.validation;

import com.abluepoint.summer.mvc.validate.AbstractStyle;
import com.abluepoint.summer.mvc.validate.Style;
import com.abluepoint.summer.mvc.validate.Styles;
import java.util.Map;
import org.springframework.core.convert.ConversionException;
import org.springframework.core.convert.support.DefaultConversionService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class RequestFieldStyles {

    @Bean
    Style requestTextStyle() {
        return Styles.string().build();
    }

    @Bean
    Style requestIntegerStyle() {
        return Styles.integer().build();
    }

    @Bean
    Style requestLongStyle() {
        return Styles.longValue().build();
    }

    @Bean
    Style requestIntegerParameterStyle() {
        return new NumberParameterStyle(Integer.class, "an integer");
    }

    @Bean
    Style requestLongParameterStyle() {
        return new NumberParameterStyle(Long.class, "a long integer");
    }

    // Use the same conversion as MVC for leading zeros, signs and empty/defaulted parameters.
    private static final class NumberParameterStyle extends AbstractStyle {
        NumberParameterStyle(Class<? extends Number> type, String description) {
            super(type, description, Map.of());
        }

        @Override
        public boolean validate(String value) {
            if (value == null) return false;
            try {
                getTypedObject(value);
                return true;
            } catch (ConversionException invalid) {
                return false;
            }
        }

        @Override
        public Object getTypedObject(String value) {
            return DefaultConversionService.getSharedInstance().convert(value, getParamType());
        }
    }
}
