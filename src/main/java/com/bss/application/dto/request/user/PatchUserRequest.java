package com.bss.application.dto.request.user;

import com.bss.domain.user.Role;
import com.bss.domain.user.UserStatus;
import jakarta.validation.constraints.Email;

public record PatchUserRequest(
    String name,

    @Email(message = "Invalid email format")
    String email,

    Role role,

    UserStatus status
) {}
