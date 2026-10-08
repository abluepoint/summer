package com.abluepoint.summer.sample.authorization;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Optional;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class AuthorizationChangeEventTerminalTest {
    @Test void deadLetterIsTerminalUntilAnExplicitRetry() {
        var receipts = mock(AuthorizationChangeEventReceiptRepository.class);
        var receipt = new AuthorizationChangeEventReceiptEntity("event", Instant.now());
        receipt.retry("failed", false); receipt.retry("failed", false); receipt.retry("failed", true);
        when(receipts.findById("event")).thenReturn(Optional.of(receipt));
        var consumer = new AuthorizationChangeEventConsumer(receipts);
        var event = new AuthorizationChangeEventBody("event", "ent-acme", "ROLE", "role", "UPDATED", 1, "request");
        assertThat(consumer.consume(event, ignored -> { throw new AssertionError("terminal event was executed"); }))
                .isEqualTo(AuthorizationChangeEventConsumeResult.DEAD_LETTER);
        assertThat(receipt.attempts()).isEqualTo(3);
        verify(receipts, never()).saveAndFlush(any());
    }

    @Test void operatorRetryResetsOnlyTheDeadLetterReceipt() {
        var outbox = mock(AuthorizationChangeEventOutboxRepository.class);
        var receipts = mock(AuthorizationChangeEventReceiptRepository.class);
        var receipt = new AuthorizationChangeEventReceiptEntity("event", Instant.now());
        receipt.retry("failed", true);
        var row = new AuthorizationChangeEventOutboxEntity("event", "ent-acme", "ROLE", "role", "UPDATED", 1, "request", "{}", Instant.now());
        when(outbox.findById("event")).thenReturn(Optional.of(row));
        when(outbox.saveAndFlush(row)).thenReturn(row);
        when(receipts.findById("event")).thenReturn(Optional.of(receipt));
        new AuthorizationChangeEventService(outbox, new ObjectMapper(), receipts).retry("ent-acme", "event");
        verify(receipts).delete(receipt);
        assertThat(row.status()).isEqualTo(AuthorizationChangeEventStatus.PENDING);
    }
}
