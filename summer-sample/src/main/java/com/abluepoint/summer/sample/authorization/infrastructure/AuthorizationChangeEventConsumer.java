package com.abluepoint.summer.sample.authorization.infrastructure;

import com.abluepoint.summer.sample.authorization.application.AuthorizationChangeEvent;
import com.abluepoint.summer.sample.authorization.application.AuthorizationChangeEventConsumeResult;

import com.abluepoint.summer.sample.authorization.infrastructure.persistence.AuthorizationChangeEventReceiptEntity;
import com.abluepoint.summer.sample.authorization.infrastructure.persistence.AuthorizationChangeEventReceiptRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.function.Consumer;

@Service
public class AuthorizationChangeEventConsumer {
    private static final int MAX_ATTEMPTS = 3;
    private final AuthorizationChangeEventReceiptRepository receipts;

    public AuthorizationChangeEventConsumer(AuthorizationChangeEventReceiptRepository receipts) { this.receipts = receipts; }

    @Transactional
    public AuthorizationChangeEventConsumeResult consume(AuthorizationChangeEvent event, Consumer<AuthorizationChangeEvent> handler) {
        AuthorizationChangeEventReceiptEntity existing = receipts.findById(event.eventId()).orElse(null);
        if (existing != null && existing.status() == AuthorizationChangeReceiptStatus.PROCESSED) return AuthorizationChangeEventConsumeResult.DUPLICATE;
        if (existing != null && existing.status() == AuthorizationChangeReceiptStatus.DEAD_LETTER) return AuthorizationChangeEventConsumeResult.DEAD_LETTER;
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
