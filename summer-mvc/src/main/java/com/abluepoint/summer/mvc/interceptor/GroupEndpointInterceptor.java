package com.abluepoint.summer.mvc.interceptor;

import com.abluepoint.summer.mvc.annotation.AnnotationMetadataManager;
import com.abluepoint.summer.mvc.annotation.Interceptors;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * 动态GroupEndpointInterceptor
 * 获取method上@Interceptors,在这里动态执行
 */
public class GroupEndpointInterceptor implements HandlerInterceptor {


    public GroupEndpointInterceptor() {

    }

    public GroupEndpointInterceptor(InterceptorGroupManager interceptorGroupManager) {
        this.interceptorGroupManager = interceptorGroupManager;
    }

    private InterceptorGroupManager interceptorGroupManager;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {


        if (handler instanceof HandlerMethod) {
            Interceptors interceptors = getInterceptorsFromHandlerMethod((HandlerMethod) handler);
            if (interceptors != null) {
                HandlerInterceptor[] groupInterceptors = getInterceptors(interceptors);

                int size = groupInterceptors.length;
                HandlerInterceptor handlerInterceptor = null;

                boolean result = false;
                for (int i = 0; i < size; i++) {

                    handlerInterceptor = groupInterceptors[i];
                    result = handlerInterceptor.preHandle(request, response, handler);
                    if (!result) {
                        break;
                    }

                }
                return result;
            }

        }

        return true;
    }

    /**
     * 添加cache支持
     * @param handler
     * @return
     */
    private Interceptors getInterceptorsFromHandlerMethod(HandlerMethod handler) {
        return AnnotationMetadataManager.INSTANCE.getInterceptors(handler);
    }

    private HandlerInterceptor[] getInterceptors(Interceptors interceptors) {
        String interceptorsName = interceptors.value();
        InterceptorGroup group = interceptorGroupManager.getInterceptorGroup(interceptorsName);
        return group.getInterceptors();
    }

    @Override
    public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler, ModelAndView modelAndView) throws Exception {
        if (handler instanceof HandlerMethod) {
            Interceptors interceptors = getInterceptorsFromHandlerMethod((HandlerMethod) handler);
            if (interceptors != null) {
                HandlerInterceptor[] groupInterceptors = getInterceptors(interceptors);
                int size = groupInterceptors.length;
                HandlerInterceptor handlerInterceptor = null;

                for (int i = 0; i < size; i++) {

                    handlerInterceptor = groupInterceptors[i];
                    handlerInterceptor.postHandle(request, response, handler, modelAndView);

                }
            }
        }

    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        if (handler instanceof HandlerMethod) {
            Interceptors interceptors = getInterceptorsFromHandlerMethod((HandlerMethod) handler);
            if (interceptors != null) {
                HandlerInterceptor[] groupInterceptors = getInterceptors(interceptors);
                int size = groupInterceptors.length;
                HandlerInterceptor handlerInterceptor = null;

                for (int i = 0; i < size; i++) {

                    handlerInterceptor = groupInterceptors[i];
                    handlerInterceptor.afterCompletion(request, response, handler, ex);

                }
            }
        }
    }

    public InterceptorGroupManager getInterceptorGroupManager() {
        return interceptorGroupManager;
    }

    public void setInterceptorGroupManager(InterceptorGroupManager interceptorGroupManager) {
        this.interceptorGroupManager = interceptorGroupManager;
    }
}
