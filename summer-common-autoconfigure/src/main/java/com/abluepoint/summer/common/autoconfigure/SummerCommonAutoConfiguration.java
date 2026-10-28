package com.abluepoint.summer.common.autoconfigure;

import com.abluepoint.summer.common.message.internal.SpringMessageResolver;
import com.abluepoint.summer.common.message.spi.MessageResolver;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@EnableConfigurationProperties(SummerCommonProperties.class)
@ConditionalOnProperty(prefix = "summer.common", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SummerCommonAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    MessageResolver summerMessageResolver(MessageSource messageSource) {
        return new SpringMessageResolver(messageSource);
    }
}
