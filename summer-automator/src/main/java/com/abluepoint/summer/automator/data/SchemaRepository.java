package com.abluepoint.summer.automator.data;

import com.abluepoint.summer.automator.model.data.Column;
import com.abluepoint.summer.automator.model.data.Table;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

public class SchemaRepository {
    private final JdbcTemplate jdbcTemplate;

    public SchemaRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<String> findTableNamesByDbName(String dbName) {
        String sql = "select TABLE_NAME as tableName ,TABLE_COMMENT as tableComment FROM `information_schema`.`TABLES` WHERE TABLE_SCHEMA= ?;";
        List<String> list = jdbcTemplate.queryForList(sql, String.class, dbName);
        return list;
    }

    public List<Table> findByDbName(String dbName) {
        String sql = "select TABLE_NAME as tableName ,TABLE_COMMENT as tableComment FROM `information_schema`.`TABLES` WHERE TABLE_SCHEMA= ?;";
        List<Table> list = jdbcTemplate.query(sql, (rs, rowNum) -> new Table(rs.getString("tableName"), rs.getString("tableComment")), dbName);
        return list;
    }

    public Table findByDbNameAndTableName(String dbName, String tableName) {
        String sql = "select TABLE_NAME as tableName ,TABLE_COMMENT as tableComment FROM `information_schema`.`TABLES` WHERE TABLE_SCHEMA= ? and TABLE_NAME= ?;";
        Table table = jdbcTemplate.queryForObject(sql, (rs, rowNum) -> new Table(rs.getString("tableName"), rs.getString("tableComment")), dbName, tableName);
        return table;
    }


    public List<Column> findColumnByDbNameAndTableName(String dbName, String tableName) {
        String sql = "select COLUMN_NAME as columnName," +
                "DATA_TYPE as columnDataType," +
                "COLUMN_KEY as columnKey," +
                "COLUMN_COMMENT as columnComment " +
                "FROM `information_schema`.`COLUMNS` " +
                "WHERE TABLE_SCHEMA= ? AND TABLE_NAME= ? ORDER BY ORDINAL_POSITION;";
        List<Column> list = jdbcTemplate.query(sql, (rs, rowNum) -> {
            Column column = new Column();
            column.setColumnName(rs.getString("columnName"));
            column.setColumnDataType(rs.getString("columnDataType"));
            column.setColumnKey(rs.getString("columnKey"));
            column.setColumnComment(rs.getString("columnComment"));
            return column;
        }, dbName, tableName);
        return list;
    }
}
