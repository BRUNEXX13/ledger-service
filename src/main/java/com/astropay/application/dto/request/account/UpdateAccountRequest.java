package com.astropay.application.dto.request.account;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record UpdateAccountRequest(
    @NotNull(message = "Balance is required")
    @Min(value = 0, message = "Balance cannot be negative")
    BigDecimal balance
) {}
