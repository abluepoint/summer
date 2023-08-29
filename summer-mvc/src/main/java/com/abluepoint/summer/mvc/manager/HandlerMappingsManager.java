package com.abluepoint.summer.mvc.manager;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.servlet.DispatcherServlet;
import org.springframework.web.servlet.HandlerExecutionChain;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.List;

public class HandlerMappingsManager {

    private DispatcherServlet dispatcherServlet;
    private List<HandlerMapping> handlerMappings;

    /**
     * @param dispatcherServlet  = WebAppContextUtil.getDispatcherServlet()
     */
    public HandlerMappingsManager(DispatcherServlet dispatcherServlet){
        this.dispatcherServlet = dispatcherServlet;
        this.handlerMappings = this.dispatcherServlet.getHandlerMappings();
    }

    public HandlerExecutionChain getMappedHandler(HttpServletRequest request) throws Exception {
        HandlerExecutionChain mappedHandler = null;
        for (HandlerMapping hm : handlerMappings) {
            if (hm instanceof RequestMappingHandlerMapping) {
                HandlerExecutionChain handler = hm.getHandler(request);
                if (handler != null) {
                    mappedHandler = handler;
                    break;
                }
            }
        }
        return mappedHandler;
    }

    public DispatcherServlet getDispatcherServlet() {
        return dispatcherServlet;
    }

    public List<HandlerMapping> getHandlerMappings() {
        return handlerMappings;
    }

}
