package com.abluepoint.summer.automator.model.data;

import java.util.ArrayList;
import java.util.List;

public class Table {

    private String tableName;
    private String tableComment;
    private boolean logicalDeletion;
    private boolean relationship;
    private List<Column> columnList;
    private List<Column> primaryColumnList;

    public Table(String tableName, String tableComment) {
        this.tableName = tableName;
        this.tableComment = tableComment;
        this.logicalDeletion = tableComment.contains("logicalDel");
        this.relationship = tableComment.contains("relationship");
    }

    public String getTableName() {
        return tableName;
    }

    public void setTableName(String tableName) {
        this.tableName = tableName;
    }

    public String getTableComment() {
        return tableComment;
    }

    public void setTableComment(String tableComment) {
        this.tableComment = tableComment;
    }

    public boolean isLogicalDeletion() {
        return logicalDeletion;
    }

    public void setLogicalDeletion(boolean logicalDeletion) {
        this.logicalDeletion = logicalDeletion;
    }

    public boolean isRelationship() {
        return relationship;
    }

    public void setRelationship(boolean relationship) {
        this.relationship = relationship;
    }

    public List<Column> getColumnList() {
        return columnList;
    }

    public void setColumnList(List<Column> columnList) {
        this.columnList = columnList;
    }

    public List<Column> getPrimaryColumnList() {
        if (primaryColumnList == null) {
            primaryColumnList = new ArrayList<>(columnList.size());
            for (Column column : columnList) {
                if (column.getColumnKey() != null & column.getColumnKey().equals("PRI")) {

                }
            }

        }
        return primaryColumnList;
    }

    @Override
    public String toString() {
        return "Table{" +
                "tableName='" + tableName + '\'' +
                ", tableComment='" + tableComment + '\'' +
                ", logicalDeletion=" + logicalDeletion +
                ", relationship=" + relationship +
                ", columnList=" + columnList +
                '}';
    }
}
