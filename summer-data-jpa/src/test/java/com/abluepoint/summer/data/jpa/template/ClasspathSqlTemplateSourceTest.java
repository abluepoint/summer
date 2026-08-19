package com.abluepoint.summer.data.jpa.template;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ClasspathSqlTemplateSourceTest {

    @Test
    void rejectsDuplicateTemplateNames() {
        String sql = """
                -- findCustomer
                select 1
                -- findCustomer
                select 2
                """;
        ResourceLoader resourceLoader = new ResourceLoader() {
            @Override
            public Resource getResource(String location) {
                return new ByteArrayResource(sql.getBytes(StandardCharsets.UTF_8));
            }

            @Override
            public ClassLoader getClassLoader() {
                return getClass().getClassLoader();
            }
        };
        ClasspathSqlTemplateSource source = new ClasspathSqlTemplateSource(resourceLoader);

        assertThatThrownBy(() -> source.load("memory:customers.sql", "findCustomer"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Duplicate SQL template 'findCustomer'");
    }
}
