package com.abluepoint.summer.sample.cache.configuration;

import com.abluepoint.summer.sample.cache.service.VersionedTwoLevelCache;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import tools.jackson.databind.ObjectMapper;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(VersionedTwoLevelCacheProperties.class)
class VersionedTwoLevelCacheConfiguration {
    @Bean
    VersionedTwoLevelCache versionedTwoLevelCache(
            StringRedisTemplate redis,
            ObjectMapper objectMapper,
            VersionedTwoLevelCacheProperties properties) {
        return new VersionedTwoLevelCache(redis, objectMapper, properties.getTtl(), properties.getNodeId());
    }
}
