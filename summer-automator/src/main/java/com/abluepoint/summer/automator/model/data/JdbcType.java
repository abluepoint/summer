package com.abluepoint.summer.automator.model.data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/*
 * File: Constant.java
 * @author bluepoint
 * @since 2022-07-01
 *
 * Copyright (c) 2022 abluepoint teleinfo All Rights Reserved.
 */

public class JdbcType {
    public static final Map<String, Class> MYSQL_JAVA_TYPE = new HashMap() {{
        put("char", String.class);
        put("varchar", String.class);
        put("tinyint", Short.class);
        put("smallint", Short.class);
        put("int", Integer.class);
        put("float", Float.class);
        put("bigint", Long.class);
        put("double", Double.class);
        put("bit", Boolean.class);
        put("date", LocalDate.class);
        put("datetime", LocalDateTime.class);
        put("timestamp", LocalDateTime.class);
        put("text", String.class);
        put("longtext", String.class);
        put("decimal", BigDecimal.class);
        put("blob", String.class);
    }};
}
