package com.bankplatform.processing.api;

import com.bankplatform.processing.ledger.ProcessedTransaction;
import org.mapstruct.Mapper;

@Mapper
public interface ProcessingMapper {

    ProcessedTransactionResponse toResponse(ProcessedTransaction processed);
}
