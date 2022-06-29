package com.abluepoint.summer.common.util;

import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.util.Assert;

public abstract class AppContextUtil {
    private static ApplicationContext applicationContext;

    public static ApplicationContext getAppContext() {
        Assert.isTrue(AppContextUtil.isInit(), "No initialization running in the spring environment");
        return applicationContext;
    }

    public static boolean isInit() {
        return applicationContext != null;
    }

    public static <T> T getBean(Class<T> requiredType) {
        Assert.isTrue(AppContextUtil.isInit(), "No initialization running in the spring environment");
        return applicationContext.getBean(requiredType);
    }

    public static Object getBean(String name) {
        Assert.isTrue(AppContextUtil.isInit(), "No initialization running in the spring environment");
        return applicationContext.getBean(name);
    }

    public static <T> T getBean(String name, Class<T> requiredType) {
        Assert.isTrue(AppContextUtil.isInit(), "No initialization running in the spring environment");
        return applicationContext.getBean(name, requiredType);
    }

    public static class ContextHolder implements ApplicationContextAware {
        @Override
        public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
            if (AppContextUtil.applicationContext == null) {
                AppContextUtil.applicationContext = applicationContext;
            }
        }
    }
}