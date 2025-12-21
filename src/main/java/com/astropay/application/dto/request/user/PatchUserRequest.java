package com.astropay.application.dto.request.user;

import com.astropay.application.util.AppConstants;
import com.astropay.domain.model.user.Role;
import com.astropay.domain.model.user.UserStatus;
import jakarta.validation.constraints.Email;

public record PatchUserRequest(
    String name,

    @Email(message = AppConstants.INVALID_EMAIL_MSG)
    String email,

    Role role,

    UserStatus status
) {}
