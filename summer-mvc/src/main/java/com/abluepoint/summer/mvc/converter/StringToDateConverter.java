package com.abluepoint.summer.mvc.converter;

import org.springframework.core.convert.converter.Converter;

import java.text.SimpleDateFormat;
import java.util.Date;

public class StringToDateConverter implements Converter<String, Date> {

    private static final String SIMPLE_DATE_FORMAT = "yyyy-MM-dd HH:mm:ss";
    private static final String SHORT_DATE_FORMAT = "yyyy-MM-dd";
    private static final String JS_DATE_FORMAT = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'";

    private static final String SPECIAL_CHAR_1 = "-";
    private static final String SPECIAL_CHAR_2 = "T";
    private static final String SPECIAL_CHAR_3 = ":";

    private static final String REGEX_LONG = "^\\d+$";

    @Override
    public Date convert(String source) {
        if (source==null||source.isEmpty()) {
            return null;
        }
        source = source.trim();
        try {
            if (source.contains(SPECIAL_CHAR_1)) {
                SimpleDateFormat formatter;
                if (source.contains(SPECIAL_CHAR_2)) {
                    formatter = new SimpleDateFormat(JS_DATE_FORMAT);
                } else if (source.contains(SPECIAL_CHAR_3)) {
                    formatter = new SimpleDateFormat(SIMPLE_DATE_FORMAT);
                } else {
                    formatter = new SimpleDateFormat(SHORT_DATE_FORMAT);
                }
                return formatter.parse(source);
            } else if (source.matches(REGEX_LONG)) {
                Long lDate = new Long(source);
                return new Date(lDate);
            }
        } catch (Exception e) {
            throw new RuntimeException(String.format("parser %s to Date fail", source));
        }
        throw new RuntimeException(String.format("parser %s to Date fail", source));
    }

}