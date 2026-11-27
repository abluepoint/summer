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
    Style fileKeyStyle() {
        return Styles.string().maxLength(1024).build();
    }

    @Bean
    Style fileRepositoryNameStyle() {
        return Styles.string().nonBlank().maxLength(100).build();
    }

    @Bean
    Style s3AccessKeyIdStyle() {
        return Styles.string().nonBlank().maxLength(256).build();
    }

    @Bean
    Style s3SecretAccessKeyStyle() {
        return Styles.string().nonBlank().maxLength(512).build();
    }

    @Bean
    Style fileRepositoryIdStyle() {
        return Styles.string().matches(java.util.regex.Pattern.compile("[a-f0-9]{8}(?:-[a-f0-9]{4}){3}-[a-f0-9]{12}"), "a repository UUID").build();
    }

    @Bean
    Style fileRepositoryPathStyle() {
        return Styles.string().nonBlank().maxLength(128).build();
    }

    @Bean
    Style fileRepositoryTypeStyle() {
        return Styles.string().oneOf("LOCAL", "S3").build();
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

    @Bean
    Style platformDepartmentIdStyle() {
        return Styles.string().nonBlank().maxLength(64).build();
    }

    @Bean
    Style platformAccountNameStyle() {
        return Styles.string().nonBlank().maxLength(128).build();
    }

    @Bean
    Style platformDirectoryTextStyle() {
        return Styles.string().maxLength(200).build();
    }

    @Bean
    Style groupDescriptionStyle() {
        return Styles.string().maxLength(500).build();
    }

    @Bean
    Style groupEditOperationStyle() {
        return Styles.string().oneOf("DETAILS", "ROLES").build();
    }

    @Bean
    Style platformDepartmentOrderStyle() {
        return Styles.integer().min(0).build();
    }

    @Bean
    Style platformMembershipActionStyle() {
        return Styles.string().oneOf("JOIN", "LEAVE", "PRIMARY", "LEADER", "CLEAR_LEADER", "TRANSFER").build();
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
