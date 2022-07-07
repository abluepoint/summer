package com.abluepoint.summer.automator.manager;

import com.abluepoint.summer.automator.data.SchemaRepository;
import com.abluepoint.summer.automator.model.data.Column;
import com.abluepoint.summer.automator.model.data.Table;
import lombok.RequiredArgsConstructor;

import java.util.List;

@RequiredArgsConstructor
public class SchemaManager {
    private final SchemaRepository schemaRepository;

    public List<String> getTableNameList(String dbName) {
        return schemaRepository.findTableNamesByDbName(dbName);
    }

    public Table getTable(String dbName, String tableName) {
        Table table = schemaRepository.findByDbNameAndTableName(dbName, tableName);
        List<Column> columnList = schemaRepository.findColumnByDbNameAndTableName(dbName, tableName);
        table.setColumnList(columnList);
        return table;
    }
}
