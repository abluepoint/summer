package com.abluepoint.summer.common.date;

import java.time.LocalDateTime;

/**
 * 处理系统时间对象的生成
 *
 * @author bluepoint
 */
public class DateManager {

    public LocalDateTime now() {
        return LocalDateTime.now();
    }

}
