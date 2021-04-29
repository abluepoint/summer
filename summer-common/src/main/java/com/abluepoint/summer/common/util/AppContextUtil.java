package com.abluepoint.summer.common.util;

import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

public abstract class AppContextUtil {
	private static ApplicationContext applicationContext;
	public static ApplicationContext getAppContext(){
		return applicationContext;
	}

	public static boolean isInit(){
		return applicationContext != null;
	}

	public static <T> T getBean(Class<T> requiredType){
		return applicationContext.getBean(requiredType);
	}
	public static Object getBean(String name){
		return applicationContext.getBean(name);
	}
	
	public static <T> T getBean(String name,Class<T> requiredType){
		return applicationContext.getBean(name,requiredType);
	}
	
	public static class ContextHolder implements ApplicationContextAware {
		@Override
		public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
			if(AppContextUtil.applicationContext==null){
				AppContextUtil.applicationContext = applicationContext;
			}
		}
	}
}