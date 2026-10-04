package com.abluepoint.summer.sample.systemparameter;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "summer.sample.parameter-events.scheduling-enabled=false")
class SystemParameterEventConsumerTest {
    @Autowired SystemParameterEventConsumer consumer;

    @Test
    void repeatedDeliveryIsIdempotent() {
        var event = new SystemParameterEventBody("event-duplicate", "ent-acme", "orders.page-size",
                "SYSTEM_PARAMETER_CHANGED", 2, "request-1");
        AtomicInteger handled = new AtomicInteger();

        assertThat(consumer.consume(event, ignored -> handled.incrementAndGet()))
                .isEqualTo(SystemParameterEventConsumeResult.PROCESSED);
        assertThat(consumer.consume(event, ignored -> handled.incrementAndGet()))
                .isEqualTo(SystemParameterEventConsumeResult.DUPLICATE);
        assertThat(handled).hasValue(1);
    }

    @Test
    void failedDeliveryRetriesThenMovesToDeadLetter() {
        var event = new SystemParameterEventBody("event-failure", "ent-acme", "orders.page-size",
                "SYSTEM_PARAMETER_CHANGED", 2, "request-2");

        assertThat(consumer.consume(event, ignored -> { throw new IllegalStateException("downstream"); }))
                .isEqualTo(SystemParameterEventConsumeResult.RETRY);
        assertThat(consumer.consume(event, ignored -> { throw new IllegalStateException("downstream"); }))
                .isEqualTo(SystemParameterEventConsumeResult.RETRY);
        assertThat(consumer.consume(event, ignored -> { throw new IllegalStateException("downstream"); }))
                .isEqualTo(SystemParameterEventConsumeResult.DEAD_LETTER);
    }
}
