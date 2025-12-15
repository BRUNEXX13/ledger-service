package com.astropay.application.service.user;

import com.astropay.application.controller.user.mapper.UserMapper;
import com.astropay.application.dto.request.user.CreateUserRequest;
import com.astropay.application.dto.request.user.PatchUserRequest;
import com.astropay.application.dto.request.user.UpdateUserRequest;
import com.astropay.application.dto.response.user.UserResponse;
import com.astropay.application.exception.UserNotFoundException;
import com.astropay.application.service.account.port.in.AccountService;
import com.astropay.domain.model.user.Role;
import com.astropay.domain.model.user.User;
import com.astropay.domain.model.user.UserRepository;
import com.astropay.domain.model.user.UserStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private AccountService accountService;
    @Mock
    private UserMapper userMapper;

    @InjectMocks
    private UserServiceImpl userService;

    private CreateUserRequest createUserRequest;
    private User user;

    @BeforeEach
    void setUp() {
        createUserRequest = new CreateUserRequest("John Doe", "12345678900", "john.doe@example.com");
        user = spy(new User("John Doe", "12345678900", "john.doe@example.com", Role.ROLE_EMPLOYEE));
        ReflectionTestUtils.setField(user, "id", 1L);
    }

    // Tests for createUser
    @Test
    @DisplayName("createUser should succeed and create an account")
    void createUser_shouldSucceedAndCreateAccount() {
        when(userRepository.existsByEmail(createUserRequest.email())).thenReturn(false);
        when(userRepository.existsByDocument(createUserRequest.document())).thenReturn(false);
        when(userMapper.toUser(createUserRequest)).thenReturn(user);
        when(userRepository.save(user)).thenReturn(user);

        userService.createUser(createUserRequest);

        verify(userRepository).save(user);
        verify(accountService).createAccountForUser(user, new BigDecimal("1000.00"));
        verify(userMapper).toUserResponse(user);
    }

    @Test
    @DisplayName("createUser should throw exception if email is already in use")
    void createUser_shouldThrowExceptionForExistingEmail() {
        when(userRepository.existsByEmail(createUserRequest.email())).thenReturn(true);

        assertThrows(IllegalArgumentException.class, () -> userService.createUser(createUserRequest));
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("createUser should throw exception if document is already in use")
    void createUser_shouldThrowExceptionForExistingDocument() {
        when(userRepository.existsByEmail(createUserRequest.email())).thenReturn(false);
        when(userRepository.existsByDocument(createUserRequest.document())).thenReturn(true);

        assertThrows(IllegalArgumentException.class, () -> userService.createUser(createUserRequest));
        verify(userRepository, never()).save(any());
    }

    // Tests for findUserById
    @Test
    @DisplayName("findUserById should return UserResponse when found")
    void findUserById_shouldReturnUserResponseWhenFound() {
        // Arrange
        Long userId = 1L;
        UserResponse expectedResponse = new UserResponse(userId, "John Doe", "12345678900", "john.doe@example.com", UserStatus.ACTIVE, Role.ROLE_EMPLOYEE, LocalDateTime.now(), LocalDateTime.now());
        
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userMapper.toUserResponse(user)).thenReturn(expectedResponse);

        // Act
        UserResponse actualResponse = userService.findUserById(userId);

        // Assert
        assertNotNull(actualResponse);
        verify(userRepository).findById(userId);
        verify(userMapper).toUserResponse(user);
    }

    @Test
    @DisplayName("findUserById should throw UserNotFoundException when not found")
    void findUserById_shouldThrowUserNotFoundExceptionWhenNotFound() {
        // Arrange
        Long userId = 99L;
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(UserNotFoundException.class, () -> userService.findUserById(userId));
        verify(userMapper, never()).toUserResponse(any());
    }

    // Tests for updateUser
    @Test
    @DisplayName("updateUser should succeed when role is not changed")
    void updateUser_shouldSucceedWithoutRoleChange() {
        UpdateUserRequest updateRequest = new UpdateUserRequest("John Updated", "john.updated@example.com", Role.ROLE_EMPLOYEE);
        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(user));
        when(userRepository.existsByEmail(updateRequest.email())).thenReturn(false);
        when(userRepository.save(user)).thenReturn(user);

        userService.updateUser(1L, updateRequest, 99L);

        verify(userRepository).save(user);
        verify(userMapper).toUserResponse(user);
    }

    @Test
    @DisplayName("updateUser should throw SecurityException when non-admin tries to change role")
    void updateUser_shouldThrowSecurityExceptionForNonAdminRoleChange() {
        UpdateUserRequest updateRequest = new UpdateUserRequest("John Doe", "john.doe@example.com", Role.ROLE_MANAGER);
        User executor = new User("Executor", "999", "executor@test.com", Role.ROLE_EMPLOYEE);
        
        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(user));
        when(userRepository.findById(99L)).thenReturn(Optional.of(executor));

        assertThrows(SecurityException.class, () -> userService.updateUser(1L, updateRequest, 99L));
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("updateUser should succeed when admin changes role")
    void updateUser_shouldSucceedForAdminRoleChange() {
        UpdateUserRequest updateRequest = new UpdateUserRequest("John Doe", "john.doe@example.com", Role.ROLE_MANAGER);
        User adminExecutor = new User("Admin", "000", "admin@test.com", Role.ROLE_ADMIN);
        
        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(user));
        when(userRepository.findById(99L)).thenReturn(Optional.of(adminExecutor));
        when(userRepository.save(user)).thenReturn(user);

        userService.updateUser(1L, updateRequest, 99L);

        verify(userRepository).save(user);
    }

    @Test
    @DisplayName("updateUser should throw exception if new email is already in use")
    void updateUser_shouldThrowExceptionForExistingEmail() {
        UpdateUserRequest updateRequest = new UpdateUserRequest("John Updated", "new.email@example.com", Role.ROLE_EMPLOYEE);
        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(user));
        when(userRepository.existsByEmail(updateRequest.email())).thenReturn(true);

        assertThrows(IllegalArgumentException.class, () -> userService.updateUser(1L, updateRequest, 99L));
        verify(userRepository, never()).save(any());
    }

    // Tests for patchUser
    @Test
    @DisplayName("patchUser should update only provided fields")
    void patchUser_shouldUpdateOnlyProvidedFields() {
        PatchUserRequest patchRequest = new PatchUserRequest("Patched Name", null, null, null);
        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(user));
        when(userRepository.save(user)).thenReturn(user);

        userService.patchUser(1L, patchRequest, 99L);

        verify(user).changeName("Patched Name");
        verify(user, never()).changeEmail(any());
        verify(user, never()).changeRole(any());
        verify(user, never()).activate();
        verify(user, never()).block();
        verify(userRepository).save(user);
    }

    @Test
    @DisplayName("patchUser should activate user when status is ACTIVE")
    void patchUser_shouldActivateUser() {
        PatchUserRequest patchRequest = new PatchUserRequest(null, null, null, UserStatus.ACTIVE);
        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(user));
        
        userService.patchUser(1L, patchRequest, 99L);

        verify(user).activate();
        verify(user, never()).block();
        verify(userRepository).save(user);
    }

    @Test
    @DisplayName("patchUser should block user when status is not ACTIVE")
    void patchUser_shouldBlockUser() {
        PatchUserRequest patchRequest = new PatchUserRequest(null, null, null, UserStatus.BLOCKED);
        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(user));
        
        userService.patchUser(1L, patchRequest, 99L);

        verify(user).block();
        verify(user, never()).activate();
        verify(userRepository).save(user);
    }

    @Test
    @DisplayName("patchUser should throw exception if user not found")
    void patchUser_shouldThrowExceptionIfUserNotFound() {
        PatchUserRequest patchRequest = new PatchUserRequest("New Name", null, null, null);
        when(userRepository.findByIdForUpdate(99L)).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> userService.patchUser(99L, patchRequest, 1L));
    }

    // Tests for deleteUser
    @Test
    @DisplayName("deleteUser should call deleteById when user exists")
    void deleteUser_shouldCallDeleteWhenUserExists() {
        when(userRepository.existsById(1L)).thenReturn(true);
        
        userService.deleteUser(1L);

        verify(userRepository).deleteById(1L);
    }

    @Test
    @DisplayName("deleteUser should throw exception when user does not exist")
    void deleteUser_shouldThrowExceptionWhenUserDoesNotExist() {
        when(userRepository.existsById(1L)).thenReturn(false);

        assertThrows(RuntimeException.class, () -> userService.deleteUser(1L));
        verify(userRepository, never()).deleteById(anyLong());
    }

    // Tests for findAllUsers
    @Test
    @DisplayName("findAllUsers should return a page of users")
    void findAllUsers_shouldReturnPageOfUsers() {
        Page<User> userPage = new PageImpl<>(Collections.singletonList(user));
        when(userRepository.findAll(any(Pageable.class))).thenReturn(userPage);

        userService.findAllUsers(Pageable.unpaged());

        verify(userRepository).findAll(any(Pageable.class));
        verify(userMapper).toUserResponse(user);
    }
}
