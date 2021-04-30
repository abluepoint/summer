package com.abluepoint.summer.data.jpa.util;

import org.springframework.util.StringUtils;

public abstract class JpaNameUtils {

    /**
     * 转换为驼峰
     *
     * @param underscoreName
     * @return
     */
    public static String camelCaseName(String underscoreName) {
        StringBuilder result = new StringBuilder();
        if (underscoreName != null && underscoreName.length() > 0) {
            boolean flag = false;
            for (int i = 0; i < underscoreName.length(); i++) {
                char ch = underscoreName.charAt(i);
                if ("_".charAt(0) == ch) {
                    flag = true;
                } else {
                    if (flag) {
                        result.append(Character.toUpperCase(ch));
                        flag = false;
                    } else {
                        result.append(ch);
                    }
                }
            }
        }
        return result.toString();
    }

    public static String getTemplateName(String express) {
        if (StringUtils.hasText(express)) {
            int begin = express.indexOf("##");
            if (begin != -1) {
                int end = express.lastIndexOf("##");
                if (end != -1) {
                    return express.substring(begin+2,end).trim();
                }
            }
        }
        return null;
    }
}
