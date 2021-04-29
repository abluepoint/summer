/*
 * Copyright (c) 2020 abluepoint All Rights Reserved.
 * File:StaticResourceLoaderInterceptor.java
 * Date:2020-12-17 12:30:17
 */

package com.abluepoint.summer.mvc.interceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.abluepoint.summer.mvc.view.ResourceView;
import org.springframework.web.servlet.HandlerInterceptor;

import com.abluepoint.summer.mvc.view.ResourceViewFactory;

import java.util.Collections;

/**
 * @author bluepoint
 * <pre>{@code
 * @Configuration
 * public class AppWebMvcConfigurer implements WebMvcConfigurer {
 * @Value("${app.static-path}")
 * private String staticPath;
 * //	@Autowired
 * private ResourceLoader resourceLoader;
 * public void addResourceHandlers(ResourceHandlerRegistry registry) {
 * if (staticPath != null && !staticPath.equals("")) {
 * registry.setOrder(Ordered.LOWEST_PRECEDENCE).addResourceHandler("/ui/**").addResourceLocations(staticPath);
 * }
 * }
 * public void addInterceptors(InterceptorRegistry registry) {
 * InterceptorRegistration registration = registry.addInterceptor(new StaticResourceLoaderInterceptor(staticPath+"/index.html", resourceLoader));
 * registration.addPathPatterns("/ui/**");
 * registration.excludePathPatterns("/ui/static/**");
 * }
 * }
 * }</pre>
 * @see org.springframework.web.servlet.resource.ResourceHttpRequestHandler
 */
public class StaticResourceLoaderInterceptor implements HandlerInterceptor {

    private ResourceView resourceView;

    public StaticResourceLoaderInterceptor(ResourceView resourceView) {
        this.resourceView = resourceView;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        resourceView.render(Collections.emptyMap(),request,response);
        return false;
    }

}