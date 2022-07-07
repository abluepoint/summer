package com.abluepoint.summer.automator.manager;

import com.abluepoint.summer.automator.app.AppContext;
import com.abluepoint.summer.automator.app.AppContextTest;
import com.abluepoint.summer.automator.data.SchemaRepository;
import com.abluepoint.summer.automator.data.SchemaRepositoryTest;
import com.abluepoint.summer.automator.model.data.Table;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SchemaManagerTest {

    private static SchemaManager schemaManager;

    @BeforeAll
    static void beforeAll() {
        AppContextTest.beforeAll();
        schemaManager = AppContext.getAppContext().getBean(SchemaManager.class);
    }

    @Test
    void getTable() {
        Table table = schemaManager.getTable("id_test", "c_cif");
        System.out.println(table);
    }
}