package com.abluepoint.summer.sample.task;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties={"spring.flyway.enabled=true","spring.jpa.hibernate.ddl-auto=none",
        "spring.quartz.job-store-type=memory","summer.security.storage.keys=memory",
        "summer.sample.cache.enabled=false","summer.sample.resilience.enabled=false"},
        classes={com.abluepoint.summer.sample.SummerSampleApplication.class,TaskBusinessIdempotencyTest.Business.class})
@EnabledIfEnvironmentVariable(named="SUMMER_TASK_MYSQL_PASSWORD",matches=".+")
class TaskBusinessIdempotencyMySqlTest extends TaskBusinessIdempotencyTest {
    private static final String SCHEMA="summer_task_business_"+UUID.randomUUID().toString().replace("-","");
    private static final String URL="jdbc:mysql://127.0.0.1:3306/";
    private static Connection connect() throws SQLException {
        return DriverManager.getConnection(URL,"root",System.getenv("SUMMER_TASK_MYSQL_PASSWORD"));
    }
    @DynamicPropertySource static void database(DynamicPropertyRegistry properties) throws SQLException {
        try(var connection=connect();var statement=connection.createStatement()) {statement.executeUpdate("CREATE DATABASE "+SCHEMA);}
        properties.add("spring.datasource.url",()->URL+SCHEMA);
        properties.add("spring.datasource.username",()->"root");
        properties.add("spring.datasource.password",()->System.getenv("SUMMER_TASK_MYSQL_PASSWORD"));
        properties.add("spring.datasource.driver-class-name",()->"com.mysql.cj.jdbc.Driver");
    }
    @AfterAll static void cleanup() throws SQLException {
        try(var connection=connect();var statement=connection.createStatement()) {
            statement.executeUpdate("DROP DATABASE IF EXISTS "+SCHEMA);
            try(var result=statement.executeQuery("select count(*) from information_schema.schemata where schema_name='"+SCHEMA+"'")) {
                assertThat(result.next()).isTrue();assertThat(result.getInt(1)).isZero();
            }
        }
    }
}
