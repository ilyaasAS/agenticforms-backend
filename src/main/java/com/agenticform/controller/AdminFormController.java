package com.agenticform.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.agenticform.dto.AdminFormBlockRequest;
import com.agenticform.dto.AdminFormResponse;
import com.agenticform.service.AdminFormService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/admin/forms")
public class AdminFormController {

    private final AdminFormService adminFormService;

    public AdminFormController(AdminFormService adminFormService) {
        this.adminFormService = adminFormService;
    }

    @GetMapping
    public ResponseEntity<List<AdminFormResponse>> list() {
        return ResponseEntity.ok(adminFormService.list());
    }

    @PatchMapping("/{id}")
    public ResponseEntity<AdminFormResponse> setBlocked(
            @PathVariable("id") Long id,
            @Valid @RequestBody AdminFormBlockRequest request) {
        return ResponseEntity.ok(adminFormService.setBlocked(id, request.blocked()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable("id") Long id) {
        adminFormService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
