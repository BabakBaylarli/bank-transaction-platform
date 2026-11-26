package com.bankplatform.transaction.user;

import com.bankplatform.transaction.auth.dto.UserResponse;
import org.mapstruct.Mapper;

@Mapper
public interface UserMapper {

    UserResponse toResponse(User user);
}
