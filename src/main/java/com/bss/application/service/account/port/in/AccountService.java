package com.bss.application.service.account.port.in;

import com.bss.application.dto.request.account.CreateAccountRequest;
import com.bss.application.dto.request.account.UpdateAccountRequest;
import com.bss.application.dto.response.account.AccountResponse;
import com.bss.domain.model.account.Account;
import com.bss.domain.model.user.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;

public interface AccountService {

    // Alterado de void para Account para permitir reutilização eficiente
    Account createAccountForUser(User user, BigDecimal initialBalance);

    AccountResponse createAccount(CreateAccountRequest request);
    AccountResponse findAccountById(Long id);
    
    Page<AccountResponse> findAllAccounts(Pageable pageable);
    
    AccountResponse updateAccount(Long id, UpdateAccountRequest request);
    void inactivateAccount(Long id);
    void deleteAccount(Long id);
}
