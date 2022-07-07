package com.abluepoint.summer.automator.app;

import com.abluepoint.summer.automator.model.data.Table;
import com.alibaba.druid.pool.DruidDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.datasource.DataSourceUtils;

import javax.sql.DataSource;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DataAppTest {

    @Test
    void dbTest() {
        DruidDataSource dataSource = new DruidDataSource();
        dataSource.setUrl("jdbc:mysql://10.14.3.2:3306/id_test?characterEncoding=utf8&zeroDateTimeBehavior=convertToNull&useSSL=false&allowPublicKeyRetrieval=true");
        dataSource.setUsername("root");
        dataSource.setPassword("123456");
        dataSource.setDriverClassName("com.mysql.cj.jdbc.Driver");


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