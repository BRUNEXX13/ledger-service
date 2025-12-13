package com.astropay.application.controller.user;

import com.astropay.application.dto.request.user.CreateUserRequest;
import com.astropay.application.dto.request.user.PatchUserRequest;
import com.astropay.application.dto.request.user.UpdateUserRequest;
import com.astropay.application.dto.response.user.UserResponse;
import com.astropay.application.service.user.port.in.UserService;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedResourcesAssembler;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

@RestController
@RequestMapping("/users")
@Tag(name = "User Management", description = "Endpoints for creating, reading, updating, and deleting users")
@RateLimiter(name = "default") // Aplica o rate limiter padrão a todos os métodos desta classe
public class UserController {

    private final UserService userService;
    private final PagedResourcesAssembler<UserResponse> pagedResourcesAssembler;

    public UserController(UserService userService, PagedResourcesAssembler<UserResponse> pagedResourcesAssembler) {
        this.userService = userService;
        this.pagedResourcesAssembler = pagedResourcesAssembler;
    }

    @PostMapping
    @Operation(summary = "Create a new user")
    public ResponseEntity<UserResponse> createUser(@Valid @RequestBody CreateUserRequest request) {
        UserResponse createdUser = userService.createUser(request);
        createdUser.add(linkTo(methodOn(UserController.class).getUserById(createdUser.getId())).withSelfRel());
        return ResponseEntity.status(HttpStatus.CREATED).body(createdUser);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a user by ID")
    public ResponseEntity<UserResponse> getUserById(@PathVariable Long id) {
        UserResponse user = userService.findUserById(id);
        user.add(linkTo(methodOn(UserController.class).getUserById(id)).withSelfRel());
        user.add(linkTo(methodOn(UserController.class).getAllUsers(Pageable.unpaged())).withRel("all-users"));
        return ResponseEntity.ok(user);
    }

    @GetMapping
    @Operation(summary = "List all users")
    public ResponseEntity<PagedModel<EntityModel<UserResponse>>> getAllUsers(Pageable pageable) {
        Page<UserResponse> userPage = userService.findAllUsers(pageable);
        userPage.forEach(user -> user.add(linkTo(methodOn(UserController.class).getUserById(user.getId())).withSelfRel()));
        PagedModel<EntityModel<UserResponse>> pagedModel = pagedResourcesAssembler.toModel(userPage);
        return ResponseEntity.ok(pagedModel);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a user (PUT)")
    public ResponseEntity<UserResponse> updateUser(
        @PathVariable Long id, 
        @Valid @RequestBody UpdateUserRequest request,
        @RequestHeader("X-User-Id") Long executorId) {
        
        UserResponse updatedUser = userService.updateUser(id, request, executorId);
        updatedUser.add(linkTo(methodOn(UserController.class).getUserById(id)).withSelfRel());
        return ResponseEntity.ok(updatedUser);
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Partially update a user (PATCH)")
    public ResponseEntity<UserResponse> patchUser(
        @PathVariable Long id, 
        @Valid @RequestBody PatchUserRequest request,
        @RequestHeader("X-User-Id") Long executorId) {
            
        UserResponse patchedUser = userService.patchUser(id, request, executorId);
        patchedUser.add(linkTo(methodOn(UserController.class).getUserById(id)).withSelfRel());
        return ResponseEntity.ok(patchedUser);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a user")
    public ResponseEntity<Void> deleteUser(@PathVariable Long id) {
        userService.deleteUser(id);
        return ResponseEntity.noContent().build();
    }
}
