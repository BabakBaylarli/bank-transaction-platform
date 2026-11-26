package com.bankplatform.transaction.transfer;

import com.bankplatform.transaction.transfer.dto.TransactionResponse;
import org.mapstruct.Mapper;

@Mapper
public interface TransactionMapper {

    TransactionResponse toResponse(Transaction transaction);
}
