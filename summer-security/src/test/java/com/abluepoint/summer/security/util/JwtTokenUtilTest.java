package com.abluepoint.summer.security.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class JwtTokenUtilTest {
    @Test
    void test() {
        String authorization = "Bearer 123";
        if (authorization != null) {
            if (authorization.startsWith("Bearer ")) {
                authorization = authorization.substring(7);
            }else{
                authorization = null;
            }
        }
        System.out.println(authorization);
    }
}