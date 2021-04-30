package com.abluepoint.summer.data.jpa.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class JpaNameUtilsTest {

    @Test
    void getTemplateName() {
        String s = JpaNameUtils.getTemplateName("##export/test##");
        System.out.println(s);
    }
}