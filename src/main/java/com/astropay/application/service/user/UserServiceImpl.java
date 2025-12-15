package com.astropay.application.service.user;

import com.astropay.application.controller.user.mapper.UserMapper;
import com.astropay.application.dto.request.user.CreateUserRequest;
import com.astropay.application.dto.request.user.PatchUserRequest;
import com.astropay.application.dto.request.user.UpdateUserRequest;
import com.astropay.application.dto.response.user.UserResponse;
import com.astropay.application.exception.UserNotFoundException;
import com.astropay.application.service.account.port.in.AccountService;
import com.astropay.application.service.user.port.in.UserService;
import com.astropay.domain.model.user.Role;
import com.astropay.domain.model.user.User;
import com.astropay.domain.model.user.UserRepository;
import com.astropay.domain.model.user.UserStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.file.attribute.UserPrincipalNotFoundException;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

@Service
@Transactional
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final AccountService accountService;
    private final UserMapper userMapper;

    public UserServiceImpl(UserRepository userRepository, AccountService accountService, UserMapper userMapper) {
        this.userRepository = userRepository;
        this.accountService = accountService;
        this.userMapper = userMapper;
    }

    @Override
    public UserResponse createUser(CreateUserRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new IllegalArgumentException("Email already in use: " + request.email());
        }
        if (userRepository.existsByDocument(request.document())) {
            throw new IllegalArgumentException("Document already in use: " + request.document());
        }

        User newUser = userMapper.toUser(request);
        newUser.changeRole(Role.ROLE_EMPLOYEE);

        User savedUser = userRepository.save(newUser);

        accountService.createAccountForUser(savedUser, new BigDecimal("1000.00"));

        return userMapper.toUserResponse(savedUser);
    }

    @Override
    @Transactional(readOnly = true)
    public UserResponse findUserById(Long id) {
        return userRepository.findById(id)
            .map(userMapper::toUserResponse)
            .orElseThrow(() -> new UserNotFoundException("User not found with id: " + id));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<UserResponse> findAllUsers(Pageable pageable) {
        return userRepository.findAll(pageable)
            .map(userMapper::toUserResponse);
    }

    @Override
    public UserResponse updateUser(Long id, UpdateUserRequest request, Long executorId) {
        User userToUpdate = findUserForUpdate(id);
        
        validateRoleChangePermission(userToUpdate.getRole(), request.role(), executorId);
        validateEmailUniqueness(userToUpdate.getEmail(), request.email());

        userToUpdate.changeName(request.name());
        userToUpdate.changeEmail(request.email());
        userToUpdate.changeRole(request.role());

        User updatedUser = userRepository.save(userToUpdate);
        return userMapper.toUserResponse(updatedUser);
    }

    @Override
    public UserResponse patchUser(Long id, PatchUserRequest request, Long executorId) {
        User userToUpdate = findUserForUpdate(id);

        validateRoleChangePermission(userToUpdate.getRole(), request.role(), executorId);
        validateEmailUniqueness(userToUpdate.getEmail(), request.email());
        
        applyPatchChanges(userToUpdate, request);

        User patchedUser = userRepository.save(userToUpdate);
        return userMapper.toUserResponse(patchedUser);
    }

    @Override
    public void deleteUser(Long id) {
        if (!userRepository.existsById(id)) {
            throw new UserNotFoundException("User not found with id: " + id);
        }
        userRepository.deleteById(id);
    }

    // --- Helper Methods ---

    private User findUserForUpdate(Long id) {
        return userRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new RuntimeException("User not found with id: " + id));
    }

    private void validateRoleChangePermission(Role currentRole, Role newRole, Long executorId) {
        if (newRole != null && !Objects.equals(currentRole, newRole)) {
            validateAdminRole(executorId);
        }
    }

    private void validateEmailUniqueness(String currentEmail, String newEmail) {
        if (newEmail != null && !currentEmail.equals(newEmail) && userRepository.existsByEmail(newEmail)) {
            throw new IllegalArgumentException("Email already in use: " + newEmail);
        }
    }

    private void applyPatchChanges(User user, PatchUserRequest request) {
        applyIfNotNull(request.name(), user::changeName);
        applyIfNotNull(request.email(), user::changeEmail);
        applyIfNotNull(request.role(), user::changeRole);
        applyIfNotNull(request.status(), status -> updateUserStatus(user, status));
    }

    private <T> void applyIfNotNull(T value, Consumer<T> consumer) {
        Optional.ofNullable(value).ifPresent(consumer);
    }

    private void updateUserStatus(User user, UserStatus status) {
        if (status == UserStatus.ACTIVE) {
            user.activate();
        } else {
            user.block();
        }
    }

    private void validateAdminRole(Long executorId) {
        User executor = userRepository.findById(executorId)
            .orElseThrow(() -> new RuntimeException("Executor user not found with id: " + executorId));
        
        if (executor.getRole() != Role.ROLE_ADMIN) {
            throw new SecurityException("User does not have permission to change roles.");
        }
    }
}
