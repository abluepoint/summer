/*
 * Copyright (c) 2020 abluepoint All Rights Reserved.
 * File:RequestLoggingSupport.java
 * Date:2020-03-31 23:32:31
 */

package com.abluepoint.summer.mvc.interceptor;

import com.abluepoint.summer.mvc.filter.CachingSupportServletRequestWrap;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.lang.Nullable;
import org.springframework.util.StringUtils;
import org.springframework.web.util.WebUtils;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import java.io.UnsupportedEncodingException;
import java.util.Enumeration;
import java.util.function.Predicate;

public class RequestLoggingSupport {

    public static final String REQUEST_LOGGING_SUPPORT_ATTR = RequestLoggingSupport.class.getName()+".INSTANCE";
    /**
     * The default value prepended to the log message written <i>before</i> a request is
     * processed.
     */
    public static final String DEFAULT_BEFORE_MESSAGE_PREFIX = "Before request [";

    /**
     * The default value appended to the log message written <i>before</i> a request is
     * processed.
     */
    public static final String DEFAULT_BEFORE_MESSAGE_SUFFIX = "]";

    /**
     * The default value prepended to the log message written <i>after</i> a request is
     * processed.
     */
    public static final String DEFAULT_AFTER_MESSAGE_PREFIX = "After request [";

    /**
     * The default value appended to the log message written <i>after</i> a request is
     * processed.
     */
    public static final String DEFAULT_AFTER_MESSAGE_SUFFIX = "]";

    private static final int DEFAULT_MAX_PAYLOAD_LENGTH = 50;


    private boolean includeQueryString = false;

    private boolean includeClientInfo = false;

    private boolean includeHeaders = false;

    private boolean includePayload = false;

    private Predicate<String> headerPredicate;

    private int maxPayloadLength = DEFAULT_MAX_PAYLOAD_LENGTH;

    private String beforeMessagePrefix = DEFAULT_BEFORE_MESSAGE_PREFIX;

    private String beforeMessageSuffix = DEFAULT_BEFORE_MESSAGE_SUFFIX;

    private String afterMessagePrefix = DEFAULT_AFTER_MESSAGE_PREFIX;

    private String afterMessageSuffix = DEFAULT_AFTER_MESSAGE_SUFFIX;


    public String createMessage(HttpServletRequest request, String prefix, String suffix) {
        StringBuilder msg = new StringBuilder();
        msg.append(prefix);
        msg.append(request.getMethod()).append(" ");
        msg.append(request.getRequestURI());

        if (isIncludeQueryString()) {
            String queryString = request.getQueryString();
            if (queryString != null) {
                msg.append('?').append(queryString);
            }
        }

        if (isIncludeClientInfo()) {
            String client = request.getRemoteAddr();
            if (StringUtils.hasLength(client)) {
                msg.append(", client=").append(client);
            }
            HttpSession session = request.getSession(false);
            if (session != null) {
                msg.append(", session=").append(session.getId());
            }
            String user = request.getRemoteUser();
            if (user != null) {
                msg.append(", user=").append(user);
            }
        }

        if (isIncludeHeaders()) {
            HttpHeaders headers = new ServletServerHttpRequest(request).getHeaders();
            if (getHeaderPredicate() != null) {
                Enumeration<String> names = request.getHeaderNames();
                while (names.hasMoreElements()) {
                    String header = names.nextElement();
                    if (!getHeaderPredicate().test(header)) {
                        headers.set(header, "masked");
                    }
                }
            }
            msg.append(", headers=").append(headers);
        }

        if (isIncludePayload()) {
            String payload = getMessagePayload(request);
            if (payload != null) {
                msg.append(", payload=").append(payload);
            }
        }

        msg.append(suffix);
        return msg.toString();
    }

    public String getMessagePayload(HttpServletRequest request) {
        CachingSupportServletRequestWrap wrapper =
                WebUtils.getNativeRequest(request, CachingSupportServletRequestWrap.class);
        if (wrapper != null) {
            if(!wrapper.isCached()){
                wrapper.cacheRequest();
            }
            byte[] buf = wrapper.getByteArray();
            if (buf.length > 0) {
                int length = Math.min(buf.length, getMaxPayloadLength());
                try {
                    return new String(buf, 0, length, wrapper.getCharacterEncoding());
                }
                catch (UnsupportedEncodingException ex) {
                    return "[unknown]";
                }
            }
        }
        return null;
    }

    public String getBeforeMessage(HttpServletRequest request) {
        return createMessage(request, this.beforeMessagePrefix, this.beforeMessageSuffix);
    }

    public String getAfterMessage(HttpServletRequest request) {
        return createMessage(request, this.afterMessagePrefix, this.afterMessageSuffix);
    }



    public boolean isIncludeQueryString() {
        return includeQueryString;
    }

    public void setIncludeQueryString(boolean includeQueryString) {
        this.includeQueryString = includeQueryString;
    }

    public boolean isIncludeClientInfo() {
        return includeClientInfo;
    }

    public void setIncludeClientInfo(boolean includeClientInfo) {
        this.includeClientInfo = includeClientInfo;
    }

    public boolean isIncludeHeaders() {
        return includeHeaders;
    }

    public void setIncludeHeaders(boolean includeHeaders) {
        this.includeHeaders = includeHeaders;
    }

    public boolean isIncludePayload() {
        return includePayload;
    }

    public void setIncludePayload(boolean includePayload) {
        this.includePayload = includePayload;
    }

    @Nullable
    public Predicate<String> getHeaderPredicate() {
        return headerPredicate;
    }

    public void setHeaderPredicate(@Nullable Predicate<String> headerPredicate) {
        this.headerPredicate = headerPredicate;
    }

    public int getMaxPayloadLength() {
        return maxPayloadLength;
    }

    public void setMaxPayloadLength(int maxPayloadLength) {
        this.maxPayloadLength = maxPayloadLength;
    }

    public String getBeforeMessagePrefix() {
        return beforeMessagePrefix;
    }

    public void setBeforeMessagePrefix(String beforeMessagePrefix) {
        this.beforeMessagePrefix = beforeMessagePrefix;
    }

    public String getBeforeMessageSuffix() {
        return beforeMessageSuffix;
    }

    public void setBeforeMessageSuffix(String beforeMessageSuffix) {
        this.beforeMessageSuffix = beforeMessageSuffix;
    }

    public String getAfterMessagePrefix() {
        return afterMessagePrefix;
    }

    public void setAfterMessagePrefix(String afterMessagePrefix) {
        this.afterMessagePrefix = afterMessagePrefix;
    }

    public String getAfterMessageSuffix() {
        return afterMessageSuffix;
    }

    public void setAfterMessageSuffix(String afterMessageSuffix) {
        this.afterMessageSuffix = afterMessageSuffix;
    }
}
