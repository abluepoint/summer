package com.abluepoint.summer.mvc.util;

import com.abluepoint.summer.common.util.AppContextUtil;
import org.springframework.web.context.ContextLoader;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.context.support.WebApplicationContextUtils;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.DispatcherServlet;
import org.springframework.web.servlet.HandlerMapping;

import java.util.List;

public abstract class WebAppContextUtil {

    /*
     * The bean name for a DispatcherServlet that will be mapped to the root URL "/"
     * Keep same with DispatcherServletAutoConfiguration class
     */
    public static final String DEFAULT_DISPATCHER_SERVLET_BEAN_NAME = "dispatcherServlet";


    public static DispatcherServlet getDispatcherServlet(){
        DispatcherServlet dispatcherServlet = AppContextUtil.getBean(DEFAULT_DISPATCHER_SERVLET_BEAN_NAME, DispatcherServlet.class);
        return dispatcherServlet;
    }

}
