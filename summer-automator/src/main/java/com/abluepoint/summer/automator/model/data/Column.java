package com.abluepoint.summer.automator.model.data;

import com.abluepoint.summer.automator.util.NameUtils;
import com.abluepoint.summer.common.exception.CodeMessageDefault;
import com.abluepoint.summer.common.exception.SummerRuntimeException;

public class Column {
    private String columnName;
    private String columnDataType;
    private String columnKey;
    private String columnComment;

    public String getColumnName() {
        return columnName;
    }

    public void setColumnName(String columnName) {
        this.columnName = columnName;
    }

    public String getColumnDataType() {
        return columnDataType;
    }

    public void setColumnDataType(String columnDataType) {
        this.columnDataType = columnDataType;
    }

    public String getColumnKey() {
        return columnKey;
    }

    public void setColumnKey(String columnKey) {
        this.columnKey = columnKey;
    }

    public String getColumnComment() {
        return columnComment;
    }

    public void setColumnComment(String columnComment) {
        this.columnComment = columnComment;
    }

    public String getJavaFieldName() {
        return NameUtils.hump(columnName);
    }

    public String getJavaTypeSimpleName() {
        Class clazz = JdbcType.MYSQL_JAVA_TYPE.get(columnDataType);
        return clazz.getSimpleName();
    }

    public String getJavaTypeName() {
        Class clazz = JdbcType.MYSQL_JAVA_TYPE.get(columnDataType);
        if (clazz == null) {
            throw new SummerRuntimeException(new CodeMessageDefault(-1,columnDataType + " not support"));
        }
        return clazz.getName();
    }

    @Override
    public String toString() {
        return "Column{" +
                "columnName='" + columnName + '\'' +
                ", columnDataType='" + columnDataType + '\'' +
                ", columnKey='" + columnKey + '\'' +
                ", columnComment='" + columnComment + '\'' +
                '}';
    }
}
