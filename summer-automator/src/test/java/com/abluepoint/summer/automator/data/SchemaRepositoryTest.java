package com.abluepoint.summer.automator.data;

import com.abluepoint.summer.automator.app.AppContext;
import com.abluepoint.summer.automator.app.AppContextTest;
import com.abluepoint.summer.automator.model.data.Column;
import com.abluepoint.summer.automator.model.data.Table;
import com.alibaba.druid.pool.DruidDataSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class SchemaRepositoryTest {

    private static SchemaRepository schemaRepository;

    @BeforeAll
    static void beforeAll() {
        AppContextTest.beforeAll();
        schemaRepository = AppContext.getAppContext().getBean(SchemaRepository.class);
    }

    @Test
    void findByDbName() {
        List<Table> list = schemaRepository.findByDbName("id_test");
        for (Table s : list) {
            System.out.println(s);
        }
    }

    @Test
    void findByDbNameAndTableName() {
        Table table = schemaRepository.findByDbNameAndTableName("id_test", "c_cif");
        System.out.println(table);
    }

    @Test
    void findColumnByDbNameAndTableName() {
        List<Column> columnList = schemaRepository.findColumnByDbNameAndTableName("id_test", "c_cif");
        for (Column s : columnList) {
            System.out.println(s);
        }
    }
}