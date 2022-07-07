package com.abluepoint.summer.automator.app;

import com.abluepoint.summer.common.exception.CodeMessageEnum;
import com.abluepoint.summer.common.exception.SummerRuntimeException;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class AppContext {
    private static AppContext appContext;
    private Map<String, Object> container;

    private AppContext() {
        container = new ConcurrentHashMap<>();
    }

    public static AppContext getAppContext() {
        if (appContext == null) {
            synchronized (AppContext.class) {
                if (appContext == null) {
                    appContext = new AppContext();
                }
            }
        }
        return appContext;
    }

    public void registerBean(String name, Object object) {
        if (container.putIfAbsent(name, object) != null) {
            throw new SummerRuntimeException(CodeMessageEnum.SYSTEM_UNDEFINED_ERROR);
        }
    }

    public <T> T getBean(Class<T> requiredType) {
        return (T) container.get(requiredType.getName());
    }

    public Object getBean(String name) {
        return container.get(name);
    }

    public <T> T getBean(String name, Class<T> requiredType) {
        return (T) container.get(name);
    }
}
