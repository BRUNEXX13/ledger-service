package com.bss.application.controller.user;

import com.bss.application.dto.request.user.CreateUserRequest;
import com.bss.application.dto.request.user.PatchUserRequest;
import com.bss.application.dto.request.user.UpdateUserRequest;
import com.bss.application.dto.response.user.UserResponse;
import com.bss.application.exception.handler.RestExceptionHandler;
import com.bss.application.service.user.port.in.UserService;
import com.bss.domain.user.Role;
import com.bss.domain.user.UserStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedResourcesAssembler;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.PagedModel;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserController.class)
@ContextConfiguration(classes = UserControllerTest.TestConfig.class)
class UserControllerTest {

    @Configuration
    @EnableAutoConfiguration
    @Import({UserController.class, RestExceptionHandler.class})
    static class TestConfig {
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private UserService userService;
    
    @MockitoBean
    private PagedResourcesAssembler<UserResponse> pagedResourcesAssembler;

    @Test
    @DisplayName("POST /users - Should return 201 Created for valid request")
    void shouldCreateUser() throws Exception {
        CreateUserRequest request = new CreateUserRequest("John Doe", "12345678900", "john.doe@example.com");
        UserResponse response = new UserResponse(1L, "John Doe", "12345678900", "john.doe@example.com", UserStatus.ACTIVE, Role.ROLE_EMPLOYEE, LocalDateTime.now(), LocalDateTime.now());
        when(userService.createUser(any(CreateUserRequest.class))).thenReturn(response);

        mockMvc.perform(post("/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("John Doe"));
    }

    @Test
    @DisplayName("GET /users/{id} - Should return 200 OK for existing user")
    void shouldGetUserById() throws Exception {
        Long userId = 1L;
        UserResponse response = new UserResponse(userId, "John Doe", "12345678900", "john.doe@example.com", UserStatus.ACTIVE, Role.ROLE_EMPLOYEE, LocalDateTime.now(), LocalDateTime.now());
        when(userService.findUserById(userId)).thenReturn(response);

        mockMvc.perform(get("/users/{id}", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(userId));
    }

    @Test
    @DisplayName("GET /users - Should return 200 OK with a page of users")
    void shouldGetAllUsers() throws Exception {
        UserResponse user = new UserResponse(1L, "John Doe", "12345678900", "john.doe@example.com", UserStatus.ACTIVE, Role.ROLE_EMPLOYEE, LocalDateTime.now(), LocalDateTime.now());
        Page<UserResponse> page = new PageImpl<>(List.of(user));
        
        // Mock the assembler to return a simple PagedModel
        PagedModel<EntityModel<UserResponse>> pagedModel = PagedModel.of(
            List.of(EntityModel.of(user)), 
            new PagedModel.PageMetadata(1, 0, 1)
        );

        when(userService.findAllUsers(any(Pageable.class))).thenReturn(page);
        when(pagedResourcesAssembler.toModel(any(Page.class))).thenReturn(pagedModel);

        mockMvc.perform(get("/users"))
                .andExpect(status().isOk())
                // Corrected JSON path to match @Relation(collectionRelation = "users")
                .andExpect(jsonPath("$._embedded.users[0].name").value("John Doe"));
    }

    @Test
    @DisplayName("PUT /users/{id} - Should return 200 OK for successful update")
    void shouldUpdateUser() throws Exception {
        Long userId = 1L;
        Long executorId = 99L;
        UpdateUserRequest request = new UpdateUserRequest("John Doe Updated", "john.doe.updated@example.com", Role.ROLE_MANAGER);
        UserResponse response = new UserResponse(userId, "John Doe Updated", "12345678900", "john.doe.updated@example.com", UserStatus.ACTIVE, Role.ROLE_MANAGER, LocalDateTime.now(), LocalDateTime.now());
        when(userService.updateUser(eq(userId), any(UpdateUserRequest.class), eq(executorId))).thenReturn(response);

        mockMvc.perform(put("/users/{id}", userId)
                .header("Executor-ID", executorId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("John Doe Updated"));
    }

    @Test
    @DisplayName("DELETE /users/{id} - Should return 204 No Content for successful deletion")
    void shouldDeleteUser() throws Exception {
        Long userId = 1L;
        doNothing().when(userService).deleteUser(userId);

        mockMvc.perform(delete("/users/{id}", userId))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("PATCH /users/{id} - Should return 200 OK for successful partial update")
    void shouldPatchUser() throws Exception {
        Long userId = 1L;
        Long executorId = 99L;
        PatchUserRequest request = new PatchUserRequest("John Doe Patched", null, null, null);
        UserResponse response = new UserResponse(userId, "John Doe Patched", "12345678900", "john.doe@example.com", UserStatus.ACTIVE, Role.ROLE_EMPLOYEE, LocalDateTime.now(), LocalDateTime.now());
        when(userService.patchUser(eq(userId), any(PatchUserRequest.class), eq(executorId))).thenReturn(response);

        mockMvc.perform(patch("/users/{id}", userId)
                        .header("Executor-ID", executorId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("John Doe Patched"));
    }
}
