package com.bankplatform.transaction.transfer;

import com.bankplatform.transaction.transfer.dto.TransactionResponse;

/** @param replayed true when the response is the stored result of an earlier request with the same key */
public record TransferResult(TransactionResponse transaction, boolean replayed) {
}
