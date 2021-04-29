package com.abluepoint.summer.mvc.manager;

import com.abluepoint.summer.mvc.util.WebAppContextUtil;
import org.springframework.web.servlet.*;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
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
