package com.astropay.application.dto.request.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record CreateUserRequest(
    @NotBlank(message = "Name is required")
    String name,

    @NotBlank(message = "Document is required")
    String document,

    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    String email
) {}
