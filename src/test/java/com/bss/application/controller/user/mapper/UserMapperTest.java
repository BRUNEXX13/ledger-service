package com.bss.application.controller.user.mapper;

import com.bss.application.dto.request.user.CreateUserRequest;
import com.bss.application.dto.response.user.UserResponse;
import com.bss.domain.user.Role;
import com.bss.domain.user.User;
import com.bss.domain.user.UserStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class UserMapperTest {

    private final UserMapper mapper = new UserMapper();

    @Test
    @DisplayName("Should map CreateUserRequest to User domain object")
    void shouldMapRequestToDomain() {
        // Arrange
        // Corrected constructor usage: only name, document, email
        CreateUserRequest request = new CreateUserRequest("John Doe", "12345678900", "john@test.com");

        // Act
        User user = mapper.toUser(request);

        // Assert
        assertNotNull(user);
        assertEquals("John Doe", user.getName());
        assertEquals("12345678900", user.getDocument());
        assertEquals("john@test.com", user.getEmail());
        assertNull(user.getRole()); // Role is null as per mapper implementation
        assertEquals(UserStatus.ACTIVE, user.getStatus()); // Default status
    }

    @Test
    @DisplayName("Should map User to UserResponse")
    void shouldMapUserToResponse() {
        // Arrange
        User user = new User("Jane Doe", "98765432100", "jane@test.com", Role.ROLE_ADMIN);
        ReflectionTestUtils.setField(user, "id", 1L);
        LocalDateTime now = LocalDateTime.now();
        ReflectionTestUtils.setField(user, "createdAt", now);
        ReflectionTestUtils.setField(user, "updatedAt", now);

        // Act
        UserResponse response = mapper.toUserResponse(user);

        // Assert
        assertNotNull(response);
        assertEquals(1L, response.getId());
        assertEquals("Jane Doe", response.getName());
        assertEquals("98765432100", response.getDocument());
        assertEquals("jane@test.com", response.getEmail());
        assertEquals(UserStatus.ACTIVE, response.getStatus());
        assertEquals(Role.ROLE_ADMIN, response.getRole());
        assertEquals(now, response.getCreatedAt());
        assertEquals(now, response.getUpdatedAt());
    }
}
