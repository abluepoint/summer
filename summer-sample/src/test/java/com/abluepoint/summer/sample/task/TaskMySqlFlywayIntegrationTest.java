package com.abluepoint.summer.sample.task;


import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.flyway.enabled=true",
        "spring.flyway.locations=classpath:db/migration/mysql",
        "spring.jpa.hibernate.ddl-auto=none"
})
@Testcontainers(disabledWithoutDocker = true)
class TaskMySqlFlywayIntegrationTest {
    @Container
    @ServiceConnection
    static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4");

    @Autowired JdbcTemplate jdbcTemplate;

    @Test
    void migrationCreatesQuartzClusterAndTaskTables() {
        assertThat(jdbcTemplate.queryForObject("select count(*) from information_schema.tables where table_schema = database() and table_name in ('QRTZ_JOB_DETAILS','QRTZ_TRIGGERS','QRTZ_FIRED_TRIGGERS','QRTZ_LOCKS','summer_task_definition','summer_task_execution','summer_task_execution_attempt','summer_task_trigger_receipt')", Integer.class)).isEqualTo(8);
        assertThat(jdbcTemplate.queryForObject("select count(*) from QRTZ_LOCKS where SCHED_NAME = 'summer-cluster'", Integer.class)).isEqualTo(5);
        assertThat(jdbcTemplate.queryForObject("select count(*) from information_schema.columns where table_schema = database() and table_name = 'summer_task_execution' and column_name in ('idempotency_key','lease_until','recovery_count')", Integer.class)).isEqualTo(3);
    }
}
