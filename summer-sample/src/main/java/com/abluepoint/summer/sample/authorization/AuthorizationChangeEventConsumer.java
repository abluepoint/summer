package com.abluepoint.summer.sample.authorization;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.function.Consumer;

@Service
class AuthorizationChangeEventConsumer {
    private static final int MAX_ATTEMPTS = 3;
    private final AuthorizationChangeEventReceiptRepository receipts;

    AuthorizationChangeEventConsumer(AuthorizationChangeEventReceiptRepository receipts) { this.receipts = receipts; }

    @Transactional
    AuthorizationChangeEventConsumeResult consume(AuthorizationChangeEventBody event, Consumer<AuthorizationChangeEventBody> handler) {
        AuthorizationChangeEventReceiptEntity existing = receipts.findById(event.eventId()).orElse(null);
        if (existing != null && existing.status() == AuthorizationChangeReceiptStatus.PROCESSED) return AuthorizationChangeEventConsumeResult.DUPLICATE;
        try {
            handler.accept(event);
            if (existing == null) existing = new AuthorizationChangeEventReceiptEntity(event.eventId(), Instant.now());
            existing.markProcessed(); receipts.saveAndFlush(existing);
            return AuthorizationChangeEventConsumeResult.PROCESSED;
        } catch (RuntimeException exception) {
            AuthorizationChangeEventReceiptEntity receipt = existing == null
                    ? new AuthorizationChangeEventReceiptEntity(event.eventId(), Instant.now()) : existing;
            receipt.retry("consumer_failed", receipt.attempts() + 1 >= MAX_ATTEMPTS); receipts.saveAndFlush(receipt);
            return receipt.status() == AuthorizationChangeReceiptStatus.DEAD_LETTER
                    ? AuthorizationChangeEventConsumeResult.DEAD_LETTER : AuthorizationChangeEventConsumeResult.RETRY;
        }
    }
}

enum AuthorizationChangeEventConsumeResult { PROCESSED, DUPLICATE, RETRY, DEAD_LETTER }
