package com.abluepoint.summer.sample.identity;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Bounded Redis controls for the shared login-session index. */
@ConfigurationProperties(prefix = "summer.sample.session-index")
public class SessionIndexProperties {
    private boolean enabled;
    private String namespace = "summer:auth";
    private long timeoutMs = 500;
    private int retries = 2;
    private int failureThreshold = 3;
    private long openDurationMs = 5_000;

    public String getNamespace() { return namespace; }
    public void setNamespace(String namespace) {
        if (namespace == null || namespace.isBlank()) throw new IllegalArgumentException("Session index namespace must not be blank");
        this.namespace = namespace;
    }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public long getTimeoutMs() { return timeoutMs; }
    public void setTimeoutMs(long timeoutMs) { this.timeoutMs = Math.max(1, timeoutMs); }
    public int getRetries() { return retries; }
    public void setRetries(int retries) { this.retries = Math.max(0, Math.min(5, retries)); }
    public int getFailureThreshold() { return failureThreshold; }
    public void setFailureThreshold(int failureThreshold) { this.failureThreshold = Math.max(1, failureThreshold); }
    public long getOpenDurationMs() { return openDurationMs; }
    public void setOpenDurationMs(long openDurationMs) { this.openDurationMs = Math.max(100, openDurationMs); }
}
