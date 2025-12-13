package com.astropay.application.controller.user.mapper;

import com.astropay.application.dto.request.user.CreateUserRequest;
import com.astropay.application.dto.response.user.UserResponse;
import com.astropay.domain.model.user.Role;
import com.astropay.domain.model.user.User;
import org.springframework.stereotype.Component;

@Component
public class UserMapper {

    public User toUser(CreateUserRequest request) {
        // A role padrão é atribuída no serviço, aqui apenas mapeamos os dados do request
        return new User(
            request.name(),
            request.document(),
            request.email(),
            null // A role será definida no serviço
        );
    }

    public UserResponse toUserResponse(User user) {
        return new UserResponse(
            user.getId(),
            user.getName(),
            user.getDocument(),
            user.getEmail(),
            user.getStatus(),
            user.getRole(),
            user.getCreatedAt(),
            user.getUpdatedAt()
        );
    }
}
