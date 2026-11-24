package com.abluepoint.summer.sample.member;

import com.abluepoint.summer.mvc.validate.Style;
import com.abluepoint.summer.mvc.validate.Styles;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class MemberFieldStyles {

    @Bean
    Style memberSubjectStyle() {
        return Styles.string().nonBlank().maxLength(128).build();
    }

    @Bean
    Style memberEmailStyle() {
        return Styles.string().nonBlank().maxLength(254).build();
    }
}
