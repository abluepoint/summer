package com.abluepoint.summer.sample.authorization;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Optional;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.abluepoint.summer.common.error.CommonErrorCode;
import com.abluepoint.summer.common.error.SummerException;
import com.abluepoint.summer.security.authorization.ResourceAuthorization.Domain;
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
        var row = new AuthorizationChangeEventOutboxEntity("event", "ENTERPRISE:ent-acme", "ROLE", "role", "UPDATED", 1, "request", "{}", Instant.now());
        when(outbox.findById("event")).thenReturn(Optional.of(row));
        when(outbox.saveAndFlush(row)).thenReturn(row);
        when(receipts.findById("event")).thenReturn(Optional.of(receipt));
        new AuthorizationChangeEventService(outbox, new ObjectMapper(), receipts, mock(AuthorizationDomainVersions.class)).retry("ent-acme", "event");
        verify(receipts).delete(receipt);
        assertThat(row.status()).isEqualTo(AuthorizationChangeEventStatus.PENDING);
    }

    @Test void bareHistoricalOwnerCannotBeRetriedAsEitherSameNamedDomain() {
        var outbox = mock(AuthorizationChangeEventOutboxRepository.class);
        var receipts = mock(AuthorizationChangeEventReceiptRepository.class);
        var row = new AuthorizationChangeEventOutboxEntity("legacy", "platform", "ROLE", "role", "UPDATED", 1, "request", "{}", Instant.now());
        when(outbox.findById("legacy")).thenReturn(Optional.of(row));
        var service = new AuthorizationChangeEventService(outbox, new ObjectMapper(), receipts, mock(AuthorizationDomainVersions.class));
        for (var domain : java.util.List.of(Domain.platform(), Domain.enterprise("platform"))) {
            assertThatThrownBy(() -> service.retry(domain, "legacy"))
                    .isInstanceOfSatisfying(SummerException.class,
                            failure -> assertThat(failure.errorCode()).isEqualTo(CommonErrorCode.NOT_FOUND));
        }
        verifyNoInteractions(receipts);
        verify(outbox, never()).saveAndFlush(any());
    }

    @Test void enterpriseStringRetryDoesNotSelectTheSameNamedPlatform() {
        var outbox = mock(AuthorizationChangeEventOutboxRepository.class);
        var receipts = mock(AuthorizationChangeEventReceiptRepository.class);
        var row = new AuthorizationChangeEventOutboxEntity("platform-event", "PLATFORM:platform", "ROLE", "role", "UPDATED", 1, "request", "{}", Instant.now());
        when(outbox.findById("platform-event")).thenReturn(Optional.of(row));
        var service = new AuthorizationChangeEventService(outbox, new ObjectMapper(), receipts, mock(AuthorizationDomainVersions.class));
        assertThatThrownBy(() -> service.retry("platform", "platform-event"))
                .isInstanceOfSatisfying(SummerException.class,
                        failure -> assertThat(failure.errorCode()).isEqualTo(CommonErrorCode.NOT_FOUND));
        verifyNoInteractions(receipts);
        verify(outbox, never()).saveAndFlush(any());
    }
}
