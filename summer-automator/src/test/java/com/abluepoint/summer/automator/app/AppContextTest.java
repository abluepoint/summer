package com.abluepoint.summer.automator.app;

import com.abluepoint.summer.automator.data.SchemaRepository;
import com.abluepoint.summer.automator.manager.SchemaManager;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.BeforeAll;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

/**
 * HikariCP
 */
public class AppContextTest {
    @BeforeAll
    public static void beforeAll() {
        AppContext appContext = AppContext.getAppContext();

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:mysql://10.14.3.2:3306/id_test?characterEncoding=utf8&zeroDateTimeBehavior=convertToNull&useSSL=false&allowPublicKeyRetrieval=true");
        config.setUsername("root");
        config.setPassword("123456");
        config.setDriverClassName("com.mysql.cj.jdbc.Driver");
        HikariDataSource dataSource = new HikariDataSource(config);

        appContext.registerBean(DataSource.class.getName(),dataSource);

        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);

        appContext.registerBean(JdbcTemplate.class.getName(),jdbcTemplate);
        SchemaRepository schemaRepository = new SchemaRepository(jdbcTemplate);

        appContext.registerBean(SchemaRepository.class.getName(),schemaRepository);

        SchemaManager schemaManager = new SchemaManager(schemaRepository);
        appContext.registerBean(SchemaManager.class.getName(),schemaManager);
    }
}