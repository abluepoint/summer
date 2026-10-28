package com.abluepoint.summer.sample.resilience.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "summer.sample.resilience")
public class ResilienceProperties {
    private boolean enabled = true;
    private Duration timeout = Duration.ofMillis(750);
    private Duration retryWait = Duration.ofMillis(50);
    private int maxAttempts = 2;
    private Duration rateLimitPeriod = Duration.ofSeconds(1);
    private int rateLimit = 50;
    private Duration circuitOpenWait = Duration.ofSeconds(5);
    private int circuitWindow = 10;
    private float circuitFailureRate = 50f;
    private int bulkhead = 8;
    private Duration fallbackTtl = Duration.ofSeconds(30);

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public Duration getTimeout() { return timeout; }
    public void setTimeout(Duration timeout) { this.timeout = timeout; }
    public Duration getRetryWait() { return retryWait; }
    public void setRetryWait(Duration retryWait) { this.retryWait = retryWait; }
    public int getMaxAttempts() { return maxAttempts; }
    public void setMaxAttempts(int maxAttempts) { this.maxAttempts = maxAttempts; }
    public Duration getRateLimitPeriod() { return rateLimitPeriod; }
    public void setRateLimitPeriod(Duration rateLimitPeriod) { this.rateLimitPeriod = rateLimitPeriod; }
    public int getRateLimit() { return rateLimit; }
    public void setRateLimit(int rateLimit) { this.rateLimit = rateLimit; }
    public Duration getCircuitOpenWait() { return circuitOpenWait; }
    public void setCircuitOpenWait(Duration circuitOpenWait) { this.circuitOpenWait = circuitOpenWait; }
    public int getCircuitWindow() { return circuitWindow; }
    public void setCircuitWindow(int circuitWindow) { this.circuitWindow = circuitWindow; }
    public float getCircuitFailureRate() { return circuitFailureRate; }
    public void setCircuitFailureRate(float circuitFailureRate) { this.circuitFailureRate = circuitFailureRate; }
    public int getBulkhead() { return bulkhead; }
    public void setBulkhead(int bulkhead) { this.bulkhead = bulkhead; }
    public Duration getFallbackTtl() { return fallbackTtl; }
    public void setFallbackTtl(Duration fallbackTtl) { this.fallbackTtl = fallbackTtl; }
}
