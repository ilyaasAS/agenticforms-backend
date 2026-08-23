package com.agenticform.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.agenticform.dto.AdminCreateUserRequest;
import com.agenticform.dto.AdminUpdateUserRequest;
import com.agenticform.dto.AdminUserResponse;
import com.agenticform.security.UserPrincipal;
import com.agenticform.service.AdminUserService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/admin/users")
public class AdminUserController {

    private final AdminUserService adminUserService;

    public AdminUserController(AdminUserService adminUserService) {
        this.adminUserService = adminUserService;
    }

    @GetMapping
    public ResponseEntity<List<AdminUserResponse>> list() {
        return ResponseEntity.ok(adminUserService.list());
    }

    @PostMapping
    public ResponseEntity<AdminUserResponse> create(@Valid @RequestBody AdminCreateUserRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(adminUserService.create(request));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<AdminUserResponse> update(
            @PathVariable("id") Long id,
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody AdminUpdateUserRequest request) {
        return ResponseEntity.ok(adminUserService.update(id, principal.getId(), request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @PathVariable("id") Long id,
            @AuthenticationPrincipal UserPrincipal principal) {
        adminUserService.delete(id, principal.getId());
        return ResponseEntity.noContent().build();
    }
}
