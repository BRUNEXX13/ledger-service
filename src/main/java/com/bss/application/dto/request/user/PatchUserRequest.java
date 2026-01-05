package com.bss.application.dto.request.user;

import com.bss.application.util.AppConstants;
import com.bss.domain.model.user.Role;
import com.bss.domain.model.user.UserStatus;
import jakarta.validation.constraints.Email;

public record PatchUserRequest(
    String name,

    @Email(message = AppConstants.INVALID_EMAIL_MSG)
    String email,

    Role role,

    UserStatus status
) {}
