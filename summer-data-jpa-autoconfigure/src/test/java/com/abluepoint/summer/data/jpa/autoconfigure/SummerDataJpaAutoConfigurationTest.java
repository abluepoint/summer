package com.abluepoint.summer.data.jpa.autoconfigure;

import com.abluepoint.summer.data.jpa.template.FreemarkerSqlRenderer;
import com.abluepoint.summer.data.jpa.template.JpaSqlTemplate;
import com.abluepoint.summer.data.jpa.template.SqlTemplateSource;
import com.abluepoint.summer.data.jpa.transform.TupleMapper;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class SummerDataJpaAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(SummerDataJpaAutoConfiguration.class));

    @Test
    void createsNoDataInfrastructureWhenDisabled() {
        contextRunner
                .withPropertyValues("summer.data.jpa.enabled=false")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(SqlTemplateSource.class);
                    assertThat(context).doesNotHaveBean(FreemarkerSqlRenderer.class);
                    assertThat(context).doesNotHaveBean(TupleMapper.class);
                    assertThat(context).doesNotHaveBean(JpaSqlTemplate.class);
                });
    }

    @Test
    void backsOffWhenApplicationProvidesDataInfrastructure() {
        EntityManager entityManager = mock(EntityManager.class);
        SqlTemplateSource templateSource = (location, templateName) -> "select 1";
        FreemarkerSqlRenderer renderer = new FreemarkerSqlRenderer();
        TupleMapper tupleMapper = new TupleMapper();
        JpaSqlTemplate sqlTemplate = new JpaSqlTemplate(entityManager, templateSource, renderer, tupleMapper);

        contextRunner
                .withBean(EntityManager.class, () -> entityManager)
                .withBean("customTemplateSource", SqlTemplateSource.class, () -> templateSource)
                .withBean("customRenderer", FreemarkerSqlRenderer.class, () -> renderer)
                .withBean("customTupleMapper", TupleMapper.class, () -> tupleMapper)
                .withBean("customSqlTemplate", JpaSqlTemplate.class, () -> sqlTemplate)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean("summerSqlTemplateSource");
                    assertThat(context).doesNotHaveBean("summerFreemarkerSqlRenderer");
                    assertThat(context).doesNotHaveBean("summerTupleMapper");
                    assertThat(context).doesNotHaveBean("summerJpaSqlTemplate");
                    assertThat(context).getBean(SqlTemplateSource.class).isSameAs(templateSource);
                    assertThat(context).getBean(FreemarkerSqlRenderer.class).isSameAs(renderer);
                    assertThat(context).getBean(TupleMapper.class).isSameAs(tupleMapper);
                    assertThat(context).getBean(JpaSqlTemplate.class).isSameAs(sqlTemplate);
                });
    }
}
