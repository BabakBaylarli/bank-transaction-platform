package com.bankplatform.transaction.account;

import com.bankplatform.transaction.account.dto.AccountResponse;
import org.mapstruct.Mapper;

@Mapper
public interface AccountMapper {

    AccountResponse toResponse(Account account);
}
