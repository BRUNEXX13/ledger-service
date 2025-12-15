package com.astropay.application.controller.user.mapper;

import com.astropay.application.dto.request.user.CreateUserRequest;
import com.astropay.application.dto.response.user.UserResponse;
import com.astropay.domain.model.user.Role;
import com.astropay.domain.model.user.User;
import org.springframework.stereotype.Component;

@Component
public class UserMapper {

    public User toUser(CreateUserRequest request) {
        // The default role is assigned in the service, here we only map the request data
        return new User(
            request.name(),
            request.document(),
            request.email(),
            null // The role will be defined in the service
        );
    }

    public UserResponse toUserResponse(User user) {
        return new UserResponse(
            user.getId(),
            user.getName(),
            user.getDocument(),
            user.getEmail(),
            user.getStatus(), // The DTO expects the Enum
            user.getRole(),   // The DTO expects the Enum
            user.getCreatedAt(),
            user.getUpdatedAt()
        );
    }
}
