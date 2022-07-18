package com.abluepoint.summer.automator.app;

import com.abluepoint.summer.automator.model.data.Table;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

class DataAppTest {

    @Test
    void dbTest() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:mysql://10.14.3.2:3306/id_test?characterEncoding=utf8&zeroDateTimeBehavior=convertToNull&useSSL=false&allowPublicKeyRetrieval=true");
        config.setUsername("root");
        config.setPassword("123456");
        config.setDriverClassName("com.mysql.cj.jdbc.Driver");
        HikariDataSource dataSource = new HikariDataSource(config);


        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);

        String sql = "select TABLE_NAME as tableName ,TABLE_COMMENT as tableComment FROM `information_schema`.`TABLES` WHERE TABLE_SCHEMA= ?;";
        List<Table> list = jdbcTemplate.query(sql, new RowMapper<Table>() {
            @Override
            public Table mapRow(ResultSet rs, int rowNum) throws SQLException {
                return new Table(rs.getString("tableName"),rs.getString("tableComment"));
            }
        }, "id_test");

        for (Table s : list) {
            System.out.println(s);
        }
    }
}