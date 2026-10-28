package com.abluepoint.summer.data.jpa.query.api;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class FreemarkerSqlRendererTest {

    private final FreemarkerSqlRenderer renderer = new FreemarkerSqlRenderer();

    @Test
    void rendersConditionalSql() {
        String sql = renderer.render(
                "findCustomer",
                "select * from customer where 1=1 <#if active>and active = true</#if>",
                Map.of("active", true)
        );

        assertThat(sql).contains("and active = true");
    }
}
