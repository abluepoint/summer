package com.abluepoint.summer.sample.systemparameter.infrastructure;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.Instant;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(name = "summer.sample.parameter-events.scheduling-enabled", havingValue = "true")
public class SystemParameterEventSchedulingConfiguration {
    private final SystemParameterEventDeliveryService delivery;

    public SystemParameterEventSchedulingConfiguration(SystemParameterEventDeliveryService delivery) {
        this.delivery = delivery;
    }

    @Scheduled(fixedDelayString = "${summer.sample.parameter-events.poll-interval-ms:5000}")
    public void publishDue() {
        delivery.publishDue(Instant.now(), 100);
    }
}
