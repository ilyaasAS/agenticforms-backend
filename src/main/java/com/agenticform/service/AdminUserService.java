package com.agenticform.service;

import java.util.List;
import java.util.Locale;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.agenticform.dto.AdminCreateUserRequest;
import com.agenticform.dto.AdminUpdateUserRequest;
import com.agenticform.dto.AdminUserResponse;
import com.agenticform.exception.AdminCommandForbiddenException;
import com.agenticform.exception.AdminEmailTakenException;
import com.agenticform.exception.UserAccountNotFoundException;
import com.agenticform.model.entity.Role;
import com.agenticform.model.entity.User;
import com.agenticform.repository.UserRepository;

@Service
public class AdminUserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final WorkspaceService workspaceService;
    private final AuthService authService;

    public AdminUserService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            WorkspaceService workspaceService,
            AuthService authService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.workspaceService = workspaceService;
        this.authService = authService;
    }

    @Transactional(readOnly = true)
    public List<AdminUserResponse> list() {
        return userRepository.findAll().stream()
                .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public AdminUserResponse create(AdminCreateUserRequest request) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        if (userRepository.existsByEmail(email)) {
            throw new AdminEmailTakenException();
        }

        User user = new User();
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(request.password()));
        user.setFullName(composeFullName(request.firstName(), request.lastName()));
        user.setRole(parseRole(request.role()));
        user.setPasswordEnabled(true);
        user.setEmailVerified(true);
        user.markEmailVerified();

        User saved = userRepository.save(user);
        workspaceService.createDefaultWorkspaceForUser(saved);
        return toResponse(saved);
    }

    @Transactional
    public AdminUserResponse update(Long targetId, Long actorId, AdminUpdateUserRequest request) {
        User user = requireUser(targetId);

        if (request.role() != null) {
            Role next = parseRole(request.role());
            if (user.getRole() == Role.ROLE_ADMIN && next != Role.ROLE_ADMIN) {
                ensureNotLastAdmin(user);
            }
            if (targetId.equals(actorId) && next != Role.ROLE_ADMIN) {
                throw new AdminCommandForbiddenException(
                        "Vous ne pouvez pas retirer votre propre rôle administrateur.");
            }
            user.setRole(next);
        }

        if (request.blocked() != null) {
            if (request.blocked() && targetId.equals(actorId)) {
                throw new AdminCommandForbiddenException("Vous ne pouvez pas bloquer votre propre compte.");
            }
            if (request.blocked() && user.getRole() == Role.ROLE_ADMIN) {
                ensureNotLastAdmin(user);
            }
            user.setBlocked(request.blocked());
            if (request.blocked()) {
                user.setTokenVersion(user.getTokenVersion() + 1);
            }
        }

        return toResponse(userRepository.save(user));
    }

    @Transactional
    public void delete(Long targetId, Long actorId) {
        if (targetId.equals(actorId)) {
            throw new AdminCommandForbiddenException("Vous ne pouvez pas supprimer votre propre compte ici.");
        }
        User user = requireUser(targetId);
        if (user.getRole() == Role.ROLE_ADMIN) {
            ensureNotLastAdmin(user);
        }
        authService.deleteUserAndOwnedWorkspaces(user);
    }

    private void ensureNotLastAdmin(User user) {
        if (user.getRole() == Role.ROLE_ADMIN && userRepository.countByRole(Role.ROLE_ADMIN) <= 1) {
            throw new AdminCommandForbiddenException(
                    "Impossible : c’est le dernier compte administrateur.");
        }
    }

    private User requireUser(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new UserAccountNotFoundException(id));
    }

    private static Role parseRole(String raw) {
        if (raw == null || raw.isBlank()) {
            return Role.ROLE_USER;
        }
        return Role.valueOf(raw.trim().toUpperCase(Locale.ROOT));
    }

    private static String composeFullName(String firstName, String lastName) {
        String first = blankToNull(firstName);
        String last = blankToNull(lastName);
        if (first == null && last == null) {
            return null;
        }
        if (first == null) {
            return last;
        }
        if (last == null) {
            return first;
        }
        return first + " " + last;
    }

    private static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private AdminUserResponse toResponse(User user) {
        return new AdminUserResponse(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getRole().name(),
                user.isBlocked(),
                user.isEmailVerified(),
                user.getCreatedAt());
    }
}
