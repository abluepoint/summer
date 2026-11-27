package com.abluepoint.summer.sample.authorization;

import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.Optional;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class AuthorizationChangeEventTerminalTest {
    @Test void deadLetterIsTerminal() {
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

}
