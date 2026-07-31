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

import com.agenticform.dto.AddMemberRequest;
import com.agenticform.dto.CreateWorkspaceRequest;
import com.agenticform.dto.TransferOwnershipRequest;
import com.agenticform.dto.UpdateMemberRoleRequest;
import com.agenticform.dto.UpdateWorkspaceRequest;
import com.agenticform.dto.WorkspaceMemberResponse;
import com.agenticform.dto.WorkspaceResponse;
import com.agenticform.dto.WorkspaceSummaryResponse;
import com.agenticform.security.UserPrincipal;
import com.agenticform.service.WorkspaceMemberService;
import com.agenticform.service.WorkspaceService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/workspaces")
public class WorkspaceController {

    private final WorkspaceService workspaceService;
    private final WorkspaceMemberService workspaceMemberService;

    public WorkspaceController(
            WorkspaceService workspaceService,
            WorkspaceMemberService workspaceMemberService) {
        this.workspaceService = workspaceService;
        this.workspaceMemberService = workspaceMemberService;
    }

    @GetMapping
    public ResponseEntity<List<WorkspaceSummaryResponse>> listMyWorkspaces(
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(workspaceService.listMyWorkspaces(requireUserId(principal)));
    }

    @PostMapping
    public ResponseEntity<WorkspaceResponse> createWorkspace(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody CreateWorkspaceRequest request) {
        WorkspaceResponse response = workspaceService.createWorkspace(requireUserId(principal), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{workspaceId}")
    public ResponseEntity<WorkspaceResponse> getWorkspace(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long workspaceId) {
        return ResponseEntity.ok(workspaceService.getWorkspace(workspaceId, requireUserId(principal)));
    }

    @PatchMapping("/{workspaceId}")
    public ResponseEntity<WorkspaceResponse> updateWorkspace(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long workspaceId,
            @Valid @RequestBody UpdateWorkspaceRequest request) {
        return ResponseEntity.ok(
                workspaceService.updateWorkspace(workspaceId, requireUserId(principal), request));
    }

    @DeleteMapping("/{workspaceId}")
    public ResponseEntity<Void> deleteWorkspace(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long workspaceId) {
        workspaceService.deleteWorkspace(workspaceId, requireUserId(principal));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{workspaceId}/transfer-ownership")
    public ResponseEntity<WorkspaceResponse> transferOwnership(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long workspaceId,
            @Valid @RequestBody TransferOwnershipRequest request) {
        return ResponseEntity.ok(
                workspaceService.transferOwnership(workspaceId, requireUserId(principal), request));
    }

    @GetMapping("/{workspaceId}/members")
    public ResponseEntity<List<WorkspaceMemberResponse>> listMembers(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long workspaceId) {
        return ResponseEntity.ok(
                workspaceMemberService.listMembers(workspaceId, requireUserId(principal)));
    }

    @PostMapping("/{workspaceId}/members")
    public ResponseEntity<WorkspaceMemberResponse> addMember(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long workspaceId,
            @Valid @RequestBody AddMemberRequest request) {
        WorkspaceMemberResponse response = workspaceMemberService.addMember(
                workspaceId, requireUserId(principal), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PatchMapping("/{workspaceId}/members/{userId}")
    public ResponseEntity<WorkspaceMemberResponse> updateMemberRole(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long workspaceId,
            @PathVariable Long userId,
            @Valid @RequestBody UpdateMemberRoleRequest request) {
        return ResponseEntity.ok(
                workspaceMemberService.updateMemberRole(
                        workspaceId, requireUserId(principal), userId, request));
    }

    @DeleteMapping("/{workspaceId}/members/{userId}")
    public ResponseEntity<Void> removeMember(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long workspaceId,
            @PathVariable Long userId) {
        workspaceMemberService.removeMember(workspaceId, requireUserId(principal), userId);
        return ResponseEntity.noContent().build();
    }

    private Long requireUserId(UserPrincipal principal) {
        if (principal == null) {
            throw new org.springframework.security.authentication.AuthenticationCredentialsNotFoundException(
                    "Authentification requise.");
        }
        return principal.getId();
    }
}
