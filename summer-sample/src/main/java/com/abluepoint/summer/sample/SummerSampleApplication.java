package com.abluepoint.summer.sample;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.modulith.Modulithic;

@Modulithic
@SpringBootApplication
@EnableConfigurationProperties(SampleDataProperties.class)
public class SummerSampleApplication {

    public static void main(String[] args) {
        SpringApplication.run(SummerSampleApplication.class, args);
    }
}
