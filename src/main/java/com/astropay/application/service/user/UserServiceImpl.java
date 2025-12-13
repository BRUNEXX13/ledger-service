package com.astropay.application.service.user;

import com.astropay.application.controller.user.mapper.UserMapper;
import com.astropay.application.dto.request.user.CreateUserRequest;
import com.astropay.application.dto.request.user.PatchUserRequest;
import com.astropay.application.dto.request.user.UpdateUserRequest;
import com.astropay.application.dto.response.user.UserResponse;
import com.astropay.application.service.user.port.in.UserService;
import com.astropay.domain.model.user.Role;
import com.astropay.domain.model.user.User;
import com.astropay.domain.model.user.UserRepository;
import com.astropay.domain.model.user.UserStatus;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

@Service
@Transactional
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;

    public UserServiceImpl(UserRepository userRepository, UserMapper userMapper) {
        this.userRepository = userRepository;
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
        return userMapper.toUserResponse(savedUser);
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = "users", key = "#id")
    public UserResponse findUserById(Long id) {
        return userRepository.findById(id)
            .map(userMapper::toUserResponse)
            .orElseThrow(() -> new RuntimeException("User not found with id: " + id));
    }

    @Override
    @Transactional(readOnly = true)
    // Não cacheamos a busca paginada por padrão, pois ela pode mudar frequentemente.
    // O cache é mais eficaz em buscas por ID.
    public Page<UserResponse> findAllUsers(Pageable pageable) {
        return userRepository.findAll(pageable)
            .map(userMapper::toUserResponse);
    }

    @Override
    @CachePut(value = "users", key = "#id")
    public UserResponse updateUser(Long id, UpdateUserRequest request, Long executorId) {
        User userToUpdate = userRepository.findByIdForUpdate(id)
            .orElseThrow(() -> new RuntimeException("User not found with id: " + id));
        
        if (!Objects.equals(userToUpdate.getRole(), request.role())) {
            validateAdminRole(executorId);
        }

        if (!userToUpdate.getEmail().equals(request.email()) && userRepository.existsByEmail(request.email())) {
            throw new IllegalArgumentException("Email already in use: " + request.email());
        }

        userToUpdate.changeName(request.name());
        userToUpdate.changeEmail(request.email());
        userToUpdate.changeRole(request.role());

        User updatedUser = userRepository.save(userToUpdate);
        return userMapper.toUserResponse(updatedUser);
    }

    @Override
    @CachePut(value = "users", key = "#id")
    public UserResponse patchUser(Long id, PatchUserRequest request, Long executorId) {
        User userToUpdate = userRepository.findByIdForUpdate(id)
            .orElseThrow(() -> new RuntimeException("User not found with id: " + id));

        if (request.role() != null && !Objects.equals(userToUpdate.getRole(), request.role())) {
            validateAdminRole(executorId);
        }

        if (request.email() != null && !userToUpdate.getEmail().equals(request.email()) && userRepository.existsByEmail(request.email())) {
            throw new IllegalArgumentException("Email already in use: " + request.email());
        }

        if (request.name() != null) userToUpdate.changeName(request.name());
        if (request.email() != null) userToUpdate.changeEmail(request.email());
        if (request.role() != null) userToUpdate.changeRole(request.role());
        if (request.status() != null) {
            if (request.status() == UserStatus.ACTIVE) userToUpdate.activate();
            else userToUpdate.block();
        }

        User patchedUser = userRepository.save(userToUpdate);
        return userMapper.toUserResponse(patchedUser);
    }

    @Override
    @CacheEvict(value = "users", key = "#id")
    public void deleteUser(Long id) {
        if (!userRepository.existsById(id)) {
            throw new RuntimeException("User not found with id: " + id);
        }
        userRepository.deleteById(id);
    }

    private void validateAdminRole(Long executorId) {
        User executor = userRepository.findById(executorId)
            .orElseThrow(() -> new RuntimeException("Executor user not found with id: " + executorId));
        
        if (executor.getRole() != Role.ROLE_ADMIN) {
            throw new SecurityException("User does not have permission to change roles.");
        }
    }
}
