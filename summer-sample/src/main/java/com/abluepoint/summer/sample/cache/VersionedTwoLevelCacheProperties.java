package com.abluepoint.summer.sample.cache;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "summer.sample.cache")
public class VersionedTwoLevelCacheProperties {
    private Duration ttl = Duration.ofMinutes(5);
    private String nodeId = "summer-sample";

    public Duration getTtl() {
        return ttl;
    }

    public void setTtl(Duration ttl) {
        this.ttl = ttl;
    }

    public String getNodeId() {
        return nodeId;
    }

    public void setNodeId(String nodeId) {
        this.nodeId = nodeId;
    }
}
