package com.abluepoint.summer.web.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("summer.web")
public class SummerWebProperties {

    private boolean enabled = true;
    private boolean cacheRequestBody = true;
    private boolean wrapResponses = true;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isCacheRequestBody() {
        return cacheRequestBody;
    }

    public void setCacheRequestBody(boolean cacheRequestBody) {
        this.cacheRequestBody = cacheRequestBody;
    }

    public boolean isWrapResponses() {
        return wrapResponses;
    }

    public void setWrapResponses(boolean wrapResponses) {
        this.wrapResponses = wrapResponses;
    }
}
