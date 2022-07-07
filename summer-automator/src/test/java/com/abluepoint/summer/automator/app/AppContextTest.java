package com.abluepoint.summer.automator.app;

import com.abluepoint.summer.automator.data.SchemaRepository;
import com.abluepoint.summer.automator.data.SchemaRepositoryTest;
import com.abluepoint.summer.automator.manager.SchemaManager;
import com.alibaba.druid.pool.DruidDataSource;
import org.junit.jupiter.api.BeforeAll;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

import static org.junit.jupiter.api.Assertions.*;

public class AppContextTest {
    @BeforeAll
    public static void beforeAll() {
        AppContext appContext = AppContext.getAppContext();

        DruidDataSource dataSource = new DruidDataSource();
        dataSource.setUrl("jdbc:mysql://10.14.3.2:3306/id_test?characterEncoding=utf8&zeroDateTimeBehavior=convertToNull&useSSL=false&allowPublicKeyRetrieval=true");
        dataSource.setUsername("root");
        dataSource.setPassword("123456");
        dataSource.setDriverClassName("com.mysql.cj.jdbc.Driver");

        appContext.registerBean(DataSource.class.getName(),dataSource);

        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);

        appContext.registerBean(JdbcTemplate.class.getName(),jdbcTemplate);
        SchemaRepository schemaRepository = new SchemaRepository(jdbcTemplate);

        appContext.registerBean(SchemaRepository.class.getName(),schemaRepository);

        SchemaManager schemaManager = new SchemaManager(schemaRepository);
        appContext.registerBean(SchemaManager.class.getName(),schemaManager);
    }
}