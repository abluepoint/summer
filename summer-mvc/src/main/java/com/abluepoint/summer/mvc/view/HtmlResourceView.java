/*
 * Copyright (c) 2020 abluepoint All Rights Reserved.
 * File:HtmlResourceView.java
 * Date:2020-12-17 18:04:17
 */

package com.abluepoint.summer.mvc.view;

import org.springframework.util.FileCopyUtils;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.servlet.View;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Map;

public class HtmlResourceView implements View {
    private byte[] content;
    private long lastModified;

    public HtmlResourceView(byte[] content, long lastModified) {
        this.content = content;
        this.lastModified = lastModified;
    }

    public String getContentType() {
        return "text/html";
    }

    public void render(Map<String, ?> model, HttpServletRequest request, HttpServletResponse response) throws Exception {
        if (new ServletWebRequest(request, response).checkNotModified(lastModified)) {
            return;
        }

        response.setContentType(this.getContentType());
        FileCopyUtils.copy(this.content, response.getOutputStream());
    }
}