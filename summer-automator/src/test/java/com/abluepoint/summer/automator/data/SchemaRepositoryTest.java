package com.abluepoint.summer.automator.data;

import com.abluepoint.summer.automator.app.AppContext;
import com.abluepoint.summer.automator.app.AppContextTest;
import com.abluepoint.summer.automator.model.data.Column;
import com.abluepoint.summer.automator.model.data.Table;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

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