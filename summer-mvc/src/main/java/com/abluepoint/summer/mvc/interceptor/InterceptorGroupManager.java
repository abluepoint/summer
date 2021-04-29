package com.abluepoint.summer.mvc.interceptor;

import com.abluepoint.summer.common.util.AppContextUtil;

public class InterceptorGroupManager {

    public InterceptorGroup getInterceptorGroup(String name) {
        try {
            InterceptorGroup group = AppContextUtil.getBean(name, InterceptorGroup.class);
            return group;
        } catch (Exception e) {
            throw new InterceptorGroupNotFoundException(new StringBuilder("group:").append(name).append(" not found").toString(), e);
        }
    }

}
