package com.bss.application.dto.request.user;

import com.bss.application.util.AppConstants;
import com.bss.domain.model.user.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record UpdateUserRequest(
    @NotBlank(message = "Name is required")
    String name,

    @NotBlank(message = "Email is required")
    @Email(message = AppConstants.INVALID_EMAIL_MSG)
    String email,

    @NotNull(message = "Role is required")
    Role role
) {}
