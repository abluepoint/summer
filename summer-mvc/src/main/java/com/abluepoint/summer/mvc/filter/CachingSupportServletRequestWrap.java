/*
 * Copyright (c) 2020 abluepoint All Rights Reserved.
 * File:CachingSupportServletRequestWrap.java
 * Date:2020-09-10 14:52:10
 */

package com.abluepoint.summer.mvc.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StreamUtils;
import org.springframework.web.util.WebUtils;

import javax.servlet.ServletInputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

/**
 * fixme: 不支持 MultipartFile
 */
@Slf4j
public class CachingSupportServletRequestWrap extends HttpServletRequestWrapper {

//    private static final String FORM_CONTENT_TYPE = "application/x-www-form-urlencoded";

    private boolean cached = false;

    private byte[] byteArray = null;

    private ServletInputStream inputStream;

    private BufferedReader reader;

    /**
     * Constructs a request object wrapping the given request.
     *
     * @param request the {@link HttpServletRequest} to be wrapped.
     * @throws IllegalArgumentException if the request is null
     */
    public CachingSupportServletRequestWrap(HttpServletRequest request) {
        super(request);
    }

    @Override
    public BufferedReader getReader() throws IOException {
        if(this.reader==null){
            this.reader = new BufferedReader(new InputStreamReader(getInputStream(), getCharacterEncoding()));
        }
        return this.reader;
    }

    @Override
    public synchronized ServletInputStream getInputStream() throws IOException {
        if (this.inputStream == null) {
            if (cached) {
                byteArray = StreamUtils.copyToByteArray(super.getInputStream());
                this.inputStream = new CachingInputStream(byteArray);
            } else {
                this.inputStream = super.getInputStream();
            }
        }
        return this.inputStream;
    }

    @Override
    public String getCharacterEncoding() {
        String enc = super.getCharacterEncoding();
        return (enc != null ? enc : WebUtils.DEFAULT_CHARACTER_ENCODING);
    }

    public boolean isCached() {
        return cached;
    }

    public void cacheRequest()  {
        if(!this.cached){
            this.cached = true;
            try {
                getInputStream();
            } catch (IOException e) {
                log.error("cache request failed",e);
                throw new RuntimeException(e);
            }
        }
    }

    public byte[] getByteArray()  {
        return byteArray;
    }

}
