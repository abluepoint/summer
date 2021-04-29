package com.abluepoint.summer.mvc.filter;

import javax.servlet.ReadListener;
import javax.servlet.ServletInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;

public class CachingInputStream extends ServletInputStream {

    private final ByteArrayInputStream cacheInputStream;


    public CachingInputStream(byte[] requestBody) {
        cacheInputStream = new ByteArrayInputStream(requestBody);
    }

    @Override
    public int readLine(byte[] b, int off, int len) throws IOException {
        if (len <= 0) {
            return 0;
        }
        int count = 0, c;

        while ((c = read()) != -1) {
            b[off++] = (byte)c;
            count++;
            if (c == '\n' || count == len) {
                break;
            }
        }
        return count > 0 ? count : -1;
    }

    @Override
    public boolean isFinished() {
        return false;
    }

    @Override
    public boolean isReady() {
        return true;
    }

    @Override
    public void setReadListener(ReadListener readListener) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int read() throws IOException {
        return cacheInputStream.read();
    }

    @Override
    public int read(byte[] b) throws IOException {
        return cacheInputStream.read(b);
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        return cacheInputStream.read(b, off, len);
    }

    @Override
    public long skip(long n) throws IOException {
        return cacheInputStream.skip(n);
    }

    @Override
    public int available() throws IOException {
        return cacheInputStream.available();
    }

    @Override
    public void close() throws IOException {
        cacheInputStream.close();
    }

    @Override
    public void mark(int readLimit) {
        cacheInputStream.mark(readLimit);
    }

    @Override
    public void reset() throws IOException {
        cacheInputStream.reset();
    }

    @Override
    public boolean markSupported() {
        return cacheInputStream.markSupported();
    }
}
