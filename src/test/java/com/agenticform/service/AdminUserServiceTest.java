package com.agenticform.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.agenticform.dto.AdminCreateUserRequest;
import com.agenticform.dto.AdminUpdateUserRequest;
import com.agenticform.exception.AdminCommandForbiddenException;
import com.agenticform.model.entity.Role;
import com.agenticform.model.entity.User;
import com.agenticform.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class AdminUserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private WorkspaceService workspaceService;

    @Mock
    private AuthService authService;

    @InjectMocks
    private AdminUserService adminUserService;

    @Test
    void createPersistsVerifiedUser() {
        given(userRepository.existsByEmail("ada@example.com")).willReturn(false);
        given(passwordEncoder.encode("Secret1!")).willReturn("hashed");
        given(userRepository.save(any(User.class))).willAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(8L);
            user.setCreatedAt(LocalDateTime.now());
            return user;
        });

        var created = adminUserService.create(new AdminCreateUserRequest(
                "ada@example.com",
                "Secret1!",
                "Ada",
                "Lovelace",
                "ROLE_USER"));

        assertEquals(8L, created.id());
        assertEquals("Ada Lovelace", created.fullName());
        assertTrue(created.emailVerified());
        verify(workspaceService).createDefaultWorkspaceForUser(any(User.class));
    }

    @Test
    void cannotDeleteSelf() {
        assertThrows(
                AdminCommandForbiddenException.class,
                () -> adminUserService.delete(3L, 3L));
        verify(authService, never()).deleteUserAndOwnedWorkspaces(any());
    }

    @Test
    void cannotDemoteLastAdmin() {
        User admin = new User();
        admin.setId(1L);
        admin.setEmail("admin@example.com");
        admin.setRole(Role.ROLE_ADMIN);
        given(userRepository.findById(1L)).willReturn(Optional.of(admin));
        given(userRepository.countByRole(Role.ROLE_ADMIN)).willReturn(1L);

        assertThrows(
                AdminCommandForbiddenException.class,
                () -> adminUserService.update(1L, 9L, new AdminUpdateUserRequest("ROLE_USER", null)));
    }
}
