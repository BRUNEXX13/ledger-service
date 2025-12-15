package com.astropay.application.dto.request.user;

import com.astropay.domain.model.user.Role;
import com.astropay.domain.model.user.UserStatus;
import jakarta.validation.constraints.Email;

public record PatchUserRequest(
    String name,

    @Email(message = "Invalid email format")
    String email,

    Role role,

    UserStatus status
) {}
