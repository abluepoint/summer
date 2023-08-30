package com.abluepoint.summer.data.jpa.test.config;

import cn.teleinfo.summer.data.jpa.JpaDefaultTemplate;
import cn.teleinfo.summer.data.jpa.JpaTemplate;
import cn.teleinfo.summer.data.jpa.template.JpaFtlTemplateSourceDefault;
import cn.teleinfo.summer.data.jpa.template.JpaSqlTemplate;
import cn.teleinfo.summer.data.jpa.template.JpaSqlTemplateDefault;
import cn.teleinfo.summer.data.jpa.template.JpaTemplateSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.data.jpa.repository.support.SummerJpaRepositoryFactoryBean;

import javax.persistence.EntityManager;


@Configuration
@EnableJpaRepositories(basePackages = {"cn.teleinfo.summer.data.jpa.test"}, repositoryFactoryBeanClass = SummerJpaRepositoryFactoryBean.class)
public class DataJpaConfig {

    @Bean
    public JpaTemplateSource jpaTemplateSource() {
        JpaFtlTemplateSourceDefault jpaTemplateSource = new JpaFtlTemplateSourceDefault();
        jpaTemplateSource.setTemplateLocation("classpath:config/sql/dm");
        jpaTemplateSource.setSuffix(".sql");
        return jpaTemplateSource;
    }

    @Bean
    public JpaTemplate jpaTemplate(EntityManager entityManager, JpaTemplateSource jpaTemplateSource) {
        JpaSqlTemplate jpaSqlTemplate = new JpaSqlTemplateDefault(entityManager);
        return new JpaDefaultTemplate(jpaSqlTemplate, jpaTemplateSource);
    }
}
