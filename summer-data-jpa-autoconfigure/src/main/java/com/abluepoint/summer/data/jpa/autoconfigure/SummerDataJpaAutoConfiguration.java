package com.abluepoint.summer.data.jpa.autoconfigure;

import com.abluepoint.summer.data.jpa.query.internal.ClasspathSqlTemplateSource;
import com.abluepoint.summer.data.jpa.query.api.FreemarkerSqlRenderer;
import com.abluepoint.summer.data.jpa.query.api.JpaSqlTemplate;
import com.abluepoint.summer.data.jpa.query.spi.SqlTemplateSource;
import jakarta.persistence.EntityManager;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.ResourceLoader;

@AutoConfiguration(afterName = "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration")
@ConditionalOnClass({EntityManager.class, FreemarkerSqlRenderer.class})
@EnableConfigurationProperties(SummerDataJpaProperties.class)
@ConditionalOnProperty(prefix = "summer.data.jpa", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SummerDataJpaAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    SqlTemplateSource summerSqlTemplateSource(ResourceLoader resourceLoader) {
        return new ClasspathSqlTemplateSource(resourceLoader);
    }

    @Bean
    @ConditionalOnMissingBean
    FreemarkerSqlRenderer summerFreemarkerSqlRenderer() {
        return new FreemarkerSqlRenderer();
    }

    @Bean
    @ConditionalOnMissingBean
    JpaSqlTemplate summerJpaSqlTemplate(
            EntityManager entityManager,
            SqlTemplateSource templateSource,
            FreemarkerSqlRenderer renderer
    ) {
        return new JpaSqlTemplate(entityManager, templateSource, renderer);
    }
}
