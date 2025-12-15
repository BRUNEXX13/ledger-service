package com.astropay.application.service.user.port.in;

import com.astropay.application.dto.request.user.CreateUserRequest;
import com.astropay.application.dto.request.user.PatchUserRequest;
import com.astropay.application.dto.request.user.UpdateUserRequest;
import com.astropay.application.dto.response.user.UserResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface UserService {
    UserResponse createUser(CreateUserRequest request);
    UserResponse findUserById(Long id);
    
    // Alterado de List para Page
    Page<UserResponse> findAllUsers(Pageable pageable);
    
    UserResponse updateUser(Long id, UpdateUserRequest request, Long executorId);
    UserResponse patchUser(Long id, PatchUserRequest request, Long executorId);
    void deleteUser(Long id);
}
