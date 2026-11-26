package com.bankplatform.common.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/**
 * Single place that defines the wire format of {@link TransactionEvent}s.
 * Producers and consumers of all services use it, so the JSON contract cannot drift.
 */
public final class EventJson {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    private EventJson() {
    }

    public static String write(TransactionEvent event) {
        try {
            return MAPPER.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialize event " + event.eventId(), e);
        }
    }

    /**
     * @throws InvalidEventException when the payload is not a valid event (poison message)
     */
    public static TransactionEvent read(String json) {
        try {
            TransactionEvent event = MAPPER.readValue(json, TransactionEvent.class);
            if (event == null || event.transactionId() == null) {
                throw new InvalidEventException("Event without transactionId", null);
            }
            return event;
        } catch (JsonProcessingException e) {
            throw new InvalidEventException("Malformed event payload: " + e.getOriginalMessage(), e);
        }
    }
}
