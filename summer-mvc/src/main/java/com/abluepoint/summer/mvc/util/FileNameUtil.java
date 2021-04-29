package com.abluepoint.summer.mvc.util;

import org.springframework.http.HttpHeaders;
import org.springframework.util.StringUtils;

import javax.servlet.http.HttpServletRequest;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.Base64;

public abstract class FileNameUtil {

    /**
     * 兼容各浏览器,保证下载文件名称不乱码
     */
    public static String encodeFileName(HttpServletRequest request, String fileName) throws UnsupportedEncodingException {
        String agent = request.getHeader(HttpHeaders.USER_AGENT);
        if (StringUtils.hasText(agent)) {
            if (-1 != agent.indexOf("MSIE") || -1 != agent.indexOf("Trident") || -1 != agent.indexOf("Edge")) {
                fileName = java.net.URLEncoder.encode(fileName, "UTF-8");
            } else if (-1 != agent.compareToIgnoreCase("Firefox")) {
                // Firefox
                fileName = "=?UTF-8?B?".concat(new String(Base64.getUrlEncoder().encode(fileName.getBytes("UTF-8")))).concat("?=");
            } else if (-1 != agent.compareToIgnoreCase("Chrome")) {
                // Chrome
                fileName = new String(fileName.getBytes(), "ISO8859-1");
            } else {
                // IE7+
                fileName = StringUtils.replace(URLEncoder.encode(fileName, "UTF-8"), "+", "%20");
            }
        } else {
            fileName = new String(fileName.getBytes("utf-8"), "iso8859-1");
        }
        return fileName;
    }
}