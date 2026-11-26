package com.bankplatform.search.index;

import com.bankplatform.common.event.CurrencyCode;
import com.bankplatform.common.event.RejectionReason;
import com.bankplatform.common.event.TransactionCompletedEvent;
import com.bankplatform.common.event.TransactionCreatedEvent;
import com.bankplatform.common.event.TransactionEvent;
import com.bankplatform.common.event.TransactionRejectedEvent;
import com.bankplatform.common.event.TransactionStatus;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.DateFormat;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;
import org.springframework.data.elasticsearch.annotations.Setting;
import org.springframework.data.elasticsearch.core.query.SeqNoPrimaryTerm;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Read model of a transaction, built from the event stream. Events of one transaction arrive on
 * different topics and may come in any order (completed before created), so every {@code apply}
 * method is a commutative merge: a final status is never downgraded and missing fields are filled in.
 */
@Getter
@NoArgsConstructor
@Document(indexName = TransactionDocument.INDEX)
@Setting(shards = 1, replicas = 0)
public class TransactionDocument {

    public static final String INDEX = "transactions";

    @Id
    private String id;

    @Field(type = FieldType.Keyword)
    private String sourceAccount;

    @Field(type = FieldType.Keyword)
    private String targetAccount;

    /** User ids allowed to see this transaction (owners of source and target account). */
    @Field(type = FieldType.Keyword)
    private List<String> participants = List.of();

    @Field(type = FieldType.Scaled_Float, scalingFactor = 100)
    private BigDecimal amount;

    @Field(type = FieldType.Keyword)
    private CurrencyCode currency;

    @Field(type = FieldType.Keyword)
    private TransactionStatus status;

    @Field(type = FieldType.Keyword)
    private RejectionReason rejectionReason;

    @Field(type = FieldType.Text)
    private String rejectionDetail;

    @Field(type = FieldType.Text)
    private String description;

    @Field(type = FieldType.Date, format = {DateFormat.date_optional_time, DateFormat.epoch_millis})
    private Instant createdAt;

    @Field(type = FieldType.Date, format = {DateFormat.date_optional_time, DateFormat.epoch_millis})
    private Instant updatedAt;

    /** Enables optimistic concurrency control on writes (if_seq_no / if_primary_term). */
    private SeqNoPrimaryTerm seqNoPrimaryTerm;

    public static TransactionDocument empty(UUID transactionId) {
        TransactionDocument doc = new TransactionDocument();
        doc.id = transactionId.toString();
        return doc;
    }

    public TransactionDocument apply(TransactionEvent event) {
        fillCommonFields(event);
        switch (event) {
            case TransactionCreatedEvent created -> {
                description = created.description();
                createdAt = created.occurredAt();
                if (status == null) {
                    status = TransactionStatus.PENDING;
                    touch(created.occurredAt());
                }
            }
            case TransactionCompletedEvent completed -> finish(TransactionStatus.COMPLETED, null, null, completed.occurredAt());
            case TransactionRejectedEvent rejected ->
                    finish(TransactionStatus.REJECTED, rejected.reason(), rejected.detail(), rejected.occurredAt());
        }
        return this;
    }

    private void finish(TransactionStatus finalStatus, RejectionReason reason, String detail, Instant at) {
        if (status != null && status.isFinal()) {
            return; // duplicate or conflicting late event - the first final decision wins
        }
        status = finalStatus;
        rejectionReason = reason;
        rejectionDetail = detail;
        touch(at);
    }

    private void fillCommonFields(TransactionEvent event) {
        sourceAccount = Objects.requireNonNullElse(sourceAccount, event.sourceAccount());
        targetAccount = Objects.requireNonNullElse(targetAccount, event.targetAccount());
        amount = Objects.requireNonNullElse(amount, event.amount());
        currency = Objects.requireNonNullElse(currency, event.currency());
        Set<String> merged = new LinkedHashSet<>(participants);
        Stream.of(event.sourceOwnerId(), event.targetOwnerId())
                .filter(Objects::nonNull)
                .map(UUID::toString)
                .forEach(merged::add);
        participants = List.copyOf(merged);
    }

    private void touch(Instant at) {
        if (updatedAt == null || (at != null && at.isAfter(updatedAt))) {
            updatedAt = at;
        }
    }
}
