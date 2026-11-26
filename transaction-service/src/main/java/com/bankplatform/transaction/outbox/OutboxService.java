package com.bankplatform.transaction.outbox;

import com.bankplatform.common.event.EventJson;
import com.bankplatform.common.event.TransactionEvent;
import com.bankplatform.common.logging.CorrelationId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

@Service
@RequiredArgsConstructor
public class OutboxService {

    private final OutboxRepository outboxRepository;
    private final Clock clock;

    /** Must run inside the business transaction that produced the event. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void enqueue(TransactionEvent event) {
        outboxRepository.save(new OutboxEvent(event.eventId(), event.transactionId(),
                event.getClass().getSimpleName(), event.topic(), event.sourceAccount(),
                EventJson.write(event), CorrelationId.current(), clock.instant()));
    }
}
