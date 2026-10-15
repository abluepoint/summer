package com.abluepoint.summer.sample.identity;

import com.abluepoint.summer.sample.SummerSampleApplication;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestContextManager;

import javax.sql.DataSource;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class H2ContextIsolationTest {
    @Test
    void closingOneContextDoesNotDropAnotherContextsTables() throws Exception {
        var first = new TestContextManager(FirstContext.class);
        var second = new TestContextManager(SecondContext.class);
        try {
            var firstApplication = first.getTestContext().getApplicationContext();
            var secondApplication = second.getTestContext().getApplicationContext();
            var firstDataSource = firstApplication.getBean(DataSource.class);
            var secondDataSource = secondApplication.getBean(DataSource.class);
            String firstUrl = databaseUrl(firstDataSource);
            String secondUrl = databaseUrl(secondDataSource);
            assertEquals(firstUrl, databaseUrl(firstDataSource));
            assertEquals(secondUrl, databaseUrl(secondDataSource));
            var secondJdbc = secondApplication.getBean(JdbcTemplate.class);
            assertNotNull(secondJdbc.queryForObject("select count(*) from summer_authorization_capability", Long.class));
            first.getTestContext().markApplicationContextDirty(DirtiesContext.HierarchyMode.EXHAUSTIVE);
            assertAll(
                    () -> assertNotEquals(firstUrl, secondUrl, "Different cached contexts require different H2 databases"),
                    () -> assertNotNull(secondJdbc.queryForObject("select count(*) from summer_authorization_capability", Long.class),
                            "Closing the first create-drop context must preserve the second context's schema"));
        } finally {
            first.getTestContext().markApplicationContextDirty(DirtiesContext.HierarchyMode.EXHAUSTIVE);
            second.getTestContext().markApplicationContextDirty(DirtiesContext.HierarchyMode.EXHAUSTIVE);
        }
    }

    private static String databaseUrl(DataSource source) throws Exception {
        try (var connection = source.getConnection()) {
            return connection.getMetaData().getURL();
        }
    }

    @SpringBootTest(classes = SummerSampleApplication.class, properties = "summer.test.context-isolation=first")
    static class FirstContext {}

    @SpringBootTest(classes = SummerSampleApplication.class, properties = "summer.test.context-isolation=second")
    static class SecondContext {}
}
