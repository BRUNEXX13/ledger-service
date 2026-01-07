package com.bss.application.controller.user.mapper;

import com.bss.application.dto.request.user.CreateUserRequest;
import com.bss.application.dto.response.user.UserResponse;
import com.bss.domain.user.Role;
import com.bss.domain.user.User;
import com.bss.domain.user.UserStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UserMapperTest {

    private final UserMapper mapper = new UserMapper();

    @Test
    @DisplayName("Should map CreateUserRequest to User entity")
    void shouldMapRequestToEntity() {
        // Arrange
        CreateUserRequest request = new CreateUserRequest("John Doe", "12345678900", "john.doe@example.com");

        // Act
        User user = mapper.toUser(request);

        // Assert
        assertNotNull(user);
        assertEquals("John Doe", user.getName());
        assertEquals("12345678900", user.getDocument());
        assertEquals("john.doe@example.com", user.getEmail());
        assertNull(user.getRole(), "Role should be null as it's set by the service");
    }

    @Test
    @DisplayName("Should map User entity to UserResponse DTO")
    void shouldMapEntityToResponse() {
        // Arrange
        User user = mock(User.class);
        when(user.getId()).thenReturn(1L);
        when(user.getName()).thenReturn("Jane Doe");
        when(user.getDocument()).thenReturn("09876543211");
        when(user.getEmail()).thenReturn("jane.doe@example.com");
        when(user.getStatus()).thenReturn(UserStatus.ACTIVE);
        when(user.getRole()).thenReturn(Role.ROLE_ADMIN);
        when(user.getCreatedAt()).thenReturn(LocalDateTime.now().minusDays(1));
        when(user.getUpdatedAt()).thenReturn(LocalDateTime.now());

        // Act
        UserResponse response = mapper.toUserResponse(user);

        // Assert
        assertNotNull(response);
        assertEquals(1L, response.getId());
        assertEquals("Jane Doe", response.getName());
        assertEquals(UserStatus.ACTIVE, response.getStatus());
        assertEquals(Role.ROLE_ADMIN, response.getRole());
    }
}
