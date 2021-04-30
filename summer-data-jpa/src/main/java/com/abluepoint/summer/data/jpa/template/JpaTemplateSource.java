package com.abluepoint.summer.data.jpa.template;

public interface JpaTemplateSource {

	/**
	 * @param templateName 模板名称,包行路径信息与模板名称
	 * @param param 构建模板的参数
	 * @return sql
	 * @throws Exception
	 */
	public String getTemplateSql(String templateName, Object param) throws Exception;
}
