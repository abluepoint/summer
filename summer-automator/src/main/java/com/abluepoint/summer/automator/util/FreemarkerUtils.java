package com.abluepoint.summer.automator.util;

import freemarker.core.ParseException;
import freemarker.template.*;

import java.io.*;
import java.util.Locale;
import java.util.Map;

public abstract class FreemarkerUtils {

	private static Configuration cfg = null;
	
	private static Configuration cfggbk = null;
	
	/**
	 * 获取freemarker的配置 freemarker本身支持classpath,目录和从ServletContext获取.
	 * 
	 * @return 返回Configuration对象
	 */
	private static Configuration getConfiguration() {
		if (null == cfg) {
			cfg = new Configuration(Configuration.DEFAULT_INCOMPATIBLE_IMPROVEMENTS);
			// 这里有三种方式读取
			// （一个文件目录）
			// cfg.setDirectoryForTemplateLoading(new File("templates"));
			// classpath下的一个目录（读取jar文件）
			cfg.setClassForTemplateLoading(FreemarkerUtils.class, "/template");
			// 相对web的根路径来说 根目录
			// cfg.setServletContextForTemplateLoading(ServletActionContext.getServletContext(),"templates");
			// setEncoding这个方法一定要设置国家及其编码，不然在flt中的中文在生成html后会变成乱码
			cfg.setEncoding(Locale.getDefault(), "UTF-8");

			// 设置异常处理器//这样的话就可以${a.b.c.d}即使没有属性也不会出错
			cfg.setTemplateExceptionHandler(TemplateExceptionHandler.IGNORE_HANDLER);

		}

		return cfg;
	}
	
	private static Configuration getConfiguration(String encoding) {
		if (null == cfggbk) {
			cfggbk = new Configuration(Configuration.DEFAULT_INCOMPATIBLE_IMPROVEMENTS);
			// 这里有三种方式读取
			// （一个文件目录）
			// cfg.setDirectoryForTemplateLoading(new File("templates"));
			// classpath下的一个目录（读取jar文件）
			cfggbk.setClassForTemplateLoading(FreemarkerUtils.class, "/template");
			// 相对web的根路径来说 根目录
			// cfg.setServletContextForTemplateLoading(ServletActionContext.getServletContext(),"templates");
			// setEncoding这个方法一定要设置国家及其编码，不然在flt中的中文在生成html后会变成乱码
			cfggbk.setEncoding(Locale.getDefault(), encoding);

			// 设置异常处理器//这样的话就可以${a.b.c.d}即使没有属性也不会出错
			cfggbk.setTemplateExceptionHandler(TemplateExceptionHandler.IGNORE_HANDLER);

		}

		return cfggbk;
	}

	/**
	 * 
	 * @param ftl
	 *            模板文件名,相对上面的模版根目录templates路径,例如/module/view.ftl
	 *            templates/module/view.ftl
	 * @param data
	 *            填充数据
	 * @param targetFile
	 *            要生成的静态文件的路径,相对设置中的根路径,例如 "jsp/user/1.html"
	 * @return
	 */
	public static boolean createFile(String ftl, Map<String, Object> data, String targetFile) {

		try {
			// 创建Template对象
			Configuration cfg = FreemarkerUtils.getConfiguration();
			Template template = cfg.getTemplate(ftl);
			// 生成静态页面
			Writer out = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(targetFile), "UTF-8"));
			template.process(data, out);
			out.flush();
			out.close();
		} catch (IOException e) {
			e.printStackTrace();
			return false;
		} catch (TemplateException e) {
			e.printStackTrace();
			return false;
		}
		return true;
	}

	public static boolean createFile(String ftl, Map<String, Object> data, File parent, String targetFile) {

		try {
			// 创建Template对象
			Configuration cfg = FreemarkerUtils.getConfiguration();
			Template template = cfg.getTemplate(ftl);
			// 生成静态页面
			File file = new File(parent, targetFile);
			if(file.exists()){
				file.delete();
			}
			
			Writer out = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file), "UTF-8"));
			template.process(data, out);
			out.flush();
			out.close();
		} catch (IOException e) {
			e.printStackTrace();
			return false;
		} catch (TemplateException e) {
			e.printStackTrace();
			return false;
		}
		return true;
	}
	
	public static boolean createFile(String ftl, Map<String, Object> data, File parent, String targetFile,String encoding) {

		try {
			// 创建Template对象
			Configuration cfg = FreemarkerUtils.getConfiguration(encoding);
			Template template = cfg.getTemplate(ftl);
			// 生成静态页面
			File file = new File(parent, targetFile);
			if(file.exists()){
				file.delete();
			}
			Writer out = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(new File(parent, targetFile)), "UTF-8"));
			template.process(data, out);
			out.flush();
			out.close();
		} catch (IOException e) {
			e.printStackTrace();
			return false;
		} catch (TemplateException e) {
			e.printStackTrace();
			return false;
		}
		return true;
	}
	
	public static boolean writeToFile(String ftl, Map<String, Object> data, File parent, String targetFile) {

		try {
			// 创建Template对象
			Configuration cfg = FreemarkerUtils.getConfiguration();
			Template template = cfg.getTemplate(ftl);
			// 生成静态页面
			Writer out = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(new File(parent, targetFile),true), "UTF-8"));
			template.process(data, out);
			out.flush();
			out.close();
		} catch (IOException e) {
			e.printStackTrace();
			return false;
		} catch (TemplateException e) {
			e.printStackTrace();
			return false;
		}
		return true;
	}

	public static String getString(String ftl, Map<String, Object> data) {
		// 创建Template对象
		Configuration cfg = FreemarkerUtils.getConfiguration();
		Template template;
		try {
			template = cfg.getTemplate(ftl);
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			// 生成静态页面
			Writer out = new BufferedWriter(new OutputStreamWriter(baos, "UTF-8"));
			template.process(data, out);
			out.flush();
			out.close();
			return new String(baos.toByteArray());
		} catch (TemplateNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (MalformedTemplateNameException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (ParseException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (TemplateException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return null;
	}

	public static byte[] getBytes(String ftl, Map<String, Object> data) {
		// 创建Template对象
		Configuration cfg = FreemarkerUtils.getConfiguration();

		try {
			Template template = cfg.getTemplate(ftl);
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			// 生成静态页面
			Writer out = new BufferedWriter(new OutputStreamWriter(baos, "UTF-8"));
			template.process(data, out);
			out.flush();
			out.close();
			return baos.toByteArray();
		} catch (TemplateNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (MalformedTemplateNameException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (ParseException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (TemplateException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return null;
	}
}