package com.bss.application.dto.response.user;

import com.bss.domain.user.Role;
import com.bss.domain.user.UserStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.hateoas.Link;

import java.time.LocalDateTime;
import java.time.Month;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class UserResponseTest {

    @Test
    @DisplayName("Should create UserResponse with all fields")
    void shouldCreateUserResponseWithAllFields() {
        // Arrange
        Long id = 1L;
        String name = "John Doe";
        String document = "12345678900";
        String email = "john@example.com";
        UserStatus status = UserStatus.ACTIVE;
        Role role = Role.ROLE_EMPLOYEE;
        LocalDateTime now = LocalDateTime.now();
        List<Link> links = List.of(Link.of("/users/1"));

        // Act
        UserResponse response = new UserResponse(id, name, document, email, status, role, now, now);
        response.setLinks(links);

        // Assert
        assertEquals(id, response.getId());
        assertEquals(name, response.getName());
        assertEquals(document, response.getDocument());
        assertEquals(email, response.getEmail());
        assertEquals(status, response.getStatus());
        assertEquals(role, response.getRole());
        assertEquals(now, response.getCreatedAt());
        assertEquals(now, response.getUpdatedAt());
        assertEquals(links, response.getLinks());
    }

    @Test
    @DisplayName("Should verify all getters and setters individually")
    void shouldVerifyAllGettersAndSetters() {
        // Arrange
        UserResponse response = new UserResponse();
        Long id = 10L;
        String name = "Test User";
        String document = "11122233344";
        String email = "test@test.com";
        UserStatus status = UserStatus.BLOCKED;
        Role role = Role.ROLE_ADMIN;
        LocalDateTime createdAt = LocalDateTime.now().minusDays(1);
        LocalDateTime updatedAt = LocalDateTime.now();
        List<Link> links = List.of(Link.of("/self"));

        // Act
        response.setId(id);
        response.setName(name);
        response.setDocument(document);
        response.setEmail(email);
        response.setStatus(status);
        response.setRole(role);
        response.setCreatedAt(createdAt);
        response.setUpdatedAt(updatedAt);
        response.setLinks(links);

        // Assert
        assertEquals(id, response.getId());
        assertEquals(name, response.getName());
        assertEquals(document, response.getDocument());
        assertEquals(email, response.getEmail());
        assertEquals(status, response.getStatus());
        assertEquals(role, response.getRole());
        assertEquals(createdAt, response.getCreatedAt());
        assertEquals(updatedAt, response.getUpdatedAt());
        assertEquals(links, response.getLinks());
    }

    @Test
    @DisplayName("Should create empty UserResponse and set fields")
    void shouldCreateEmptyUserResponseAndSetFields() {
        // Arrange
        UserResponse response = new UserResponse();
        Long id = 1L;
        String name = "Jane Doe";
        
        // Act
        response.setId(id);
        response.setName(name);

        // Assert
        assertEquals(id, response.getId());
        assertEquals(name, response.getName());
        assertNull(response.getDocument()); // Should be null as it wasn't set
    }

    @Test
    @DisplayName("Should serialize dates with correct format MM/dd/yyyy HH:mm:ss.SSS")
    void shouldSerializeDatesWithCorrectFormat() throws Exception {
        // Arrange
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());

        LocalDateTime fixedDate = LocalDateTime.of(2023, Month.OCTOBER, 25, 14, 30, 0, 0);
        
        UserResponse response = new UserResponse();
        response.setCreatedAt(fixedDate);
        response.setUpdatedAt(fixedDate);

        // Act
        String json = mapper.writeValueAsString(response);

        // Assert
        // Expected format: "10/25/2023 14:30:00.000"
        assertTrue(json.contains("10/25/2023 14:30:00.000"), "JSON should contain formatted date: " + json);
    }

    @Test
    @DisplayName("Should exclude null fields from JSON")
    void shouldExcludeNullFieldsFromJson() throws Exception {
        // Arrange
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());

        UserResponse response = new UserResponse();
        response.setName("Only Name");
        // id, document, email, etc are null

        // Act
        String json = mapper.writeValueAsString(response);

        // Assert
        assertTrue(json.contains("name"), "Should contain name");
        assertFalse(json.contains("document"), "Should not contain null document");
        assertFalse(json.contains("email"), "Should not contain null email");
    }

    @Test
    @DisplayName("Should handle HATEOAS links list")
    void shouldHandleHateoasLinks() {
        // Arrange
        UserResponse response = new UserResponse();
        Link selfLink = Link.of("http://localhost/users/1", "self");
        Link updateLink = Link.of("http://localhost/users/1", "update");

        // Act
        response.setLinks(List.of(selfLink, updateLink));

        // Assert
        assertNotNull(response.getLinks());
        assertEquals(2, response.getLinks().size());
        assertEquals("self", response.getLinks().get(0).getRel().value());
        assertEquals("update", response.getLinks().get(1).getRel().value());
    }
}
