package com.abluepoint.summer.automator.util;

public abstract class NameUtils {
	
	/**
	 * <p>
	 * aaa_bbb --> aaaBbb
	 * </p>
	 * 
	 * @return String
	 */
	public static String hump(String source) {
		char[] chars = source.toCharArray();
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < chars.length; i++) {
			if (chars[i] == '_') {
				chars[i + 1] = Character.toUpperCase(chars[i + 1]);
				continue;
			}
			sb.append(chars[i]);
		}
		return sb.toString();
	}
	
	/**
	 * <p>
	 * aaaBbb --> aaa_bbb
	 * </p>
	 * @param source
	 * @return
	 */
	public static String unHump(String source) {
		char[] chars = source.toCharArray();
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < chars.length; i++) {
			if(chars[i]==Character.toUpperCase(chars[i])) {
				sb.append("_").append(Character.toLowerCase(chars[i]));
			}else {
				sb.append(chars[i]);
			}
		}
		return sb.toString();
	}

	/**
	 * <p>
	 * aaa_bbb --> AaaBbb
	 * </p>
	 * 
	 * @return String
	 */
	public static String initCap(String source) {
		char[] chars = source.toCharArray();
		StringBuilder sb = new StringBuilder();
		if (chars.length > 0) {
			chars[0] = Character.toUpperCase(chars[0]);
			for (int i = 0; i < chars.length; i++) {
				if (chars[i] == '_') {
					chars[i + 1] = Character.toUpperCase(chars[i + 1]);
					continue;
				}
				sb.append(chars[i]);
			}
		}
		return sb.toString();
	}

	public static String toLowerCaseFirstOne(String s) {
		if (Character.isLowerCase(s.charAt(0)))
			return s;
		else
			return (new StringBuilder()).append(Character.toLowerCase(s.charAt(0))).append(s.substring(1)).toString();
	}

	public static String toUpperCaseFirstOne(String s) {
		if (Character.isUpperCase(s.charAt(0)))
			return s;
		else
			return (new StringBuilder()).append(Character.toUpperCase(s.charAt(0))).append(s.substring(1)).toString();
	}
	
	public static String package2Path(String javaPackage){
		return javaPackage.replaceAll("\\.","/");
	}

}