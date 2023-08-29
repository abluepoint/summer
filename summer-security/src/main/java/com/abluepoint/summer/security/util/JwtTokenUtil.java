package com.abluepoint.summer.security.util;

import jakarta.servlet.http.HttpServletRequest;

public abstract class JwtTokenUtil {
    public static String getJwtToken(HttpServletRequest request) {
        String authorization = request.getHeader("Authorization");
        if (authorization != null) {
            if (authorization.startsWith("Bearer ")) {
                authorization = authorization.substring(7);
            }else{
                authorization = null;
            }
        }
        return authorization;
    }
}
