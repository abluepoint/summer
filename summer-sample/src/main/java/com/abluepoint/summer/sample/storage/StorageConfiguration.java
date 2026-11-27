package com.abluepoint.summer.sample.storage;

import java.net.URI;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

@Configuration(proxyBeanMethods = false)
class StorageConfiguration {
    @Bean
    @Lazy
    S3Client repositoryS3Client(@Value("${summer.storage.s3.endpoint:}") String endpoint,
                                @Value("${summer.storage.s3.region:us-east-1}") String region) {
        return builder(endpoint, region).build();
    }

    static software.amazon.awssdk.services.s3.S3ClientBuilder builder(String endpoint, String region) {
        var builder = S3Client.builder().region(Region.of(region))
                .httpClientBuilder(UrlConnectionHttpClient.builder().connectionTimeout(Duration.ofSeconds(5)))
                .overrideConfiguration(c -> c.apiCallTimeout(Duration.ofMinutes(5)));
        if (!endpoint.isBlank()) builder.endpointOverride(URI.create(endpoint)).forcePathStyle(true);
        return builder;
    }
}
