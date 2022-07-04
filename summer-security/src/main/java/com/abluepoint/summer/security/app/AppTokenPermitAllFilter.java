package com.abluepoint.summer.security.app;

import com.abluepoint.summer.security.jwt.JwtManager;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class AppTokenPermitAllFilter extends AppJwtTokenFilter {
    private List<AntPathRequestMatcher> antPathMatcherList;

    public AppTokenPermitAllFilter(JwtManager jwtManager, AuthenticationEntryPoint authenticationEntryPoint, List<String> permitAllList) {
        super(jwtManager, authenticationEntryPoint);
        antPathMatcherList = new ArrayList<>(permitAllList.size());
        for (String one : permitAllList) {
            antPathMatcherList.add(new AntPathRequestMatcher(one));
        }
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws
            ServletException, IOException {

        for (AntPathRequestMatcher matcher : antPathMatcherList) {
            if (matcher.matches(request)) {
                filterChain.doFilter(request, response);
                return;
            }
        }
        super.doFilterInternal(request, response, filterChain);
    }
}