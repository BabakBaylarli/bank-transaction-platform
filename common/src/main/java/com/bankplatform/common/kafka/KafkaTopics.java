package com.bankplatform.common.kafka;

public final class KafkaTopics {

    public static final String TRANSACTIONS_CREATED = "transactions.created";
    public static final String TRANSACTIONS_COMPLETED = "transactions.completed";
    public static final String TRANSACTIONS_REJECTED = "transactions.rejected";
    public static final String TRANSACTIONS_CREATED_DLT = TRANSACTIONS_CREATED + ".DLT";

    private KafkaTopics() {
    }
}
