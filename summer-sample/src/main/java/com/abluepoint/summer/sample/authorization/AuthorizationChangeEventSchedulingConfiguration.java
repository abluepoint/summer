package com.abluepoint.summer.sample.authorization;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.Instant;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(name = "summer.sample.authorization-events.scheduling-enabled", havingValue = "true")
class AuthorizationChangeEventSchedulingConfiguration {
    private final AuthorizationChangeEventDeliveryService delivery;
    AuthorizationChangeEventSchedulingConfiguration(AuthorizationChangeEventDeliveryService delivery) { this.delivery = delivery; }
    @Scheduled(fixedDelayString = "${summer.sample.authorization-events.poll-interval-ms:5000}")
    void publishDue() { delivery.publishDue(Instant.now(), 100); }
}
