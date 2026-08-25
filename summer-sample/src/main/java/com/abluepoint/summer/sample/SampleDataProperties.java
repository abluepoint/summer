package com.abluepoint.summer.sample;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.Assert;

@ConfigurationProperties("summer.sample.data")
public record SampleDataProperties(String sqlResource) {

    public SampleDataProperties {
        Assert.hasText(sqlResource, "summer.sample.data.sql-resource must not be blank");
    }
}
