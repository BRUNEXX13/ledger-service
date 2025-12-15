package com.astropay.application.controller.account.mapper;

import com.astropay.application.dto.response.account.AccountResponse;
import com.astropay.domain.model.account.Account;
import org.springframework.stereotype.Component;

@Component
public class AccountMapper {

    public AccountResponse toAccountResponse(Account account) {
        return new AccountResponse(
            account.getId(),
            account.getUser().getId(),
            account.getBalance(),
            account.getStatus(),
            account.getCreatedAt(),
            account.getUpdatedAt()
        );
    }
}
