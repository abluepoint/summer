package com.abluepoint.summer.data.jpa.template;

public interface JpaTemplateSource {
	public String getTemplate(String templateName, Object param) throws Exception;
}
