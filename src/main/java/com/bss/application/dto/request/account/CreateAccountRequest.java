package com.bss.application.dto.request.account;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record CreateAccountRequest(
    @NotNull(message = "User ID is required")
    Long userId,

    @Min(value = 0, message = "Initial balance cannot be negative")
    BigDecimal initialBalance
) {}
