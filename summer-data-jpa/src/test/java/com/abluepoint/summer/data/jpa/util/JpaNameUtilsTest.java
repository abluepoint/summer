package com.abluepoint.summer.data.jpa.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class JpaNameUtilsTest {

    @Test
    void getTemplateName() {
        String s = JpaNameUtils.getTemplateName("##export/test##");
        System.out.println(s);
    }

    @Test
    void likeWrap() {
        String s = "aaaa";
        String s1 = JpaNameUtils.likeWrap(s);
        System.out.println(s1);
    }

    @Test
    void rightLikeWrap() {
        String s = "aaaa";
        String s1 = JpaNameUtils.rightLikeWrap(s);
        System.out.println(s1);
    }
}