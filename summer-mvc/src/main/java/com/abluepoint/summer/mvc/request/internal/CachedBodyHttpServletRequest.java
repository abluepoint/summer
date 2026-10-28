package com.abluepoint.summer.mvc.request.internal;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

public final class CachedBodyHttpServletRequest extends HttpServletRequestWrapper {

    public static final int DEFAULT_MAX_BODY_BYTES = 1024 * 1024;

    private final byte[] body;

    public CachedBodyHttpServletRequest(HttpServletRequest request) throws IOException {
        this(request, DEFAULT_MAX_BODY_BYTES);
    }

    public CachedBodyHttpServletRequest(HttpServletRequest request, int maxBodyBytes) throws IOException {
        super(request);
        if (maxBodyBytes < 1) {
            throw new IllegalArgumentException("maxBodyBytes must be positive");
        }
        if (request.getContentLengthLong() > maxBodyBytes) {
            throw new RequestBodyTooLargeException();
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream(Math.min(maxBodyBytes, 8192));
        byte[] buffer = new byte[8192];
        try (ServletInputStream input = request.getInputStream()) {
            while (true) {
                int remaining = (int) Math.min((long) buffer.length, (long) maxBodyBytes + 1 - output.size());
                int count = input.read(buffer, 0, remaining);
                if (count == -1) {
                    break;
                }
                if (count == 0) {
                    int one = input.read();
                    if (one == -1) {
                        break;
                    }
                    output.write(one);
                } else {
                    output.write(buffer, 0, count);
                }
                if (output.size() > maxBodyBytes) {
                    throw new RequestBodyTooLargeException();
                }
            }
        }
        this.body = output.toByteArray();
    }

    public byte[] body() {
        return body.clone();
    }

    @Override
    public ServletInputStream getInputStream() {
        ByteArrayInputStream input = new ByteArrayInputStream(body);
        return new ServletInputStream() {
            @Override
            public boolean isFinished() {
                return input.available() == 0;
            }

            @Override
            public boolean isReady() {
                return true;
            }

            @Override
            public void setReadListener(ReadListener listener) {
                if (listener == null) {
                    throw new IllegalArgumentException("ReadListener must not be null");
                }
            }

            @Override
            public int read() {
                return input.read();
            }

            @Override
            public int read(byte[] bytes, int offset, int length) {
                return input.read(bytes, offset, length);
            }
        };
    }

    @Override
    public BufferedReader getReader() {
        Charset charset = getCharacterEncoding() == null
                ? StandardCharsets.UTF_8
                : Charset.forName(getCharacterEncoding());
        return new BufferedReader(new InputStreamReader(getInputStream(), charset));
    }
}
