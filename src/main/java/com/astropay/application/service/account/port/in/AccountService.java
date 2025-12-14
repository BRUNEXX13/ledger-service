package com.astropay.application.service.account.port.in;

import com.astropay.application.dto.request.account.CreateAccountRequest;
import com.astropay.application.dto.request.account.UpdateAccountRequest;
import com.astropay.application.dto.response.account.AccountResponse;
import com.astropay.domain.model.user.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;

public interface AccountService {

    void createAccountForUser(User user, BigDecimal initialBalance);
    AccountResponse createAccount(CreateAccountRequest request);
    AccountResponse findAccountById(Long id);
    
    // Alterado de List para Page
    Page<AccountResponse> findAllAccounts(Pageable pageable);
    
    AccountResponse updateAccount(Long id, UpdateAccountRequest request);
    void inactivateAccount(Long id);
}
