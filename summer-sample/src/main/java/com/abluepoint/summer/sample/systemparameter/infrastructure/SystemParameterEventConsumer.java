package com.abluepoint.summer.sample.systemparameter.infrastructure;

import com.abluepoint.summer.sample.systemparameter.domain.SystemParameterEventBody;
import com.abluepoint.summer.sample.systemparameter.domain.SystemParameterEventConsumeResult;
import com.abluepoint.summer.sample.systemparameter.domain.SystemParameterEventReceiptStatus;
import com.abluepoint.summer.sample.systemparameter.infrastructure.persistence.SystemParameterEventReceiptEntity;
import com.abluepoint.summer.sample.systemparameter.infrastructure.persistence.SystemParameterEventReceiptRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.function.Consumer;

@Service
public class SystemParameterEventConsumer {
    private static final int MAX_ATTEMPTS = 3;
    private final SystemParameterEventReceiptRepository receipts;

    public SystemParameterEventConsumer(SystemParameterEventReceiptRepository receipts) {
        this.receipts = receipts;
    }

    @Transactional
    public SystemParameterEventConsumeResult consume(SystemParameterEventBody event,
                                               Consumer<SystemParameterEventBody> handler) {
        SystemParameterEventReceiptEntity existing = receipts.findById(event.eventId()).orElse(null);
        if (existing != null && existing.status() == SystemParameterEventReceiptStatus.PROCESSED) {
            return SystemParameterEventConsumeResult.DUPLICATE;
        }
        if (existing != null && existing.status() == SystemParameterEventReceiptStatus.DEAD_LETTER) {
            return SystemParameterEventConsumeResult.DEAD_LETTER;
        }
        try {
            handler.accept(event);
            if (existing == null) existing = new SystemParameterEventReceiptEntity(event.eventId(), Instant.now());
            existing.markProcessed();
            receipts.saveAndFlush(existing);
            return SystemParameterEventConsumeResult.PROCESSED;
        } catch (RuntimeException exception) {
            SystemParameterEventReceiptEntity receipt = existing == null
                    ? new SystemParameterEventReceiptEntity(event.eventId(), Instant.now()) : existing;
            receipt.retry("consumer_failed", receipt.attempts() + 1 >= MAX_ATTEMPTS);
            receipts.saveAndFlush(receipt);
            return receipt.status() == SystemParameterEventReceiptStatus.DEAD_LETTER
                    ? SystemParameterEventConsumeResult.DEAD_LETTER : SystemParameterEventConsumeResult.RETRY;
        }
    }
}
