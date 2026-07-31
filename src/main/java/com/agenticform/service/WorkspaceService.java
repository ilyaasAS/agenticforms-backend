package com.agenticform.service;

import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.agenticform.dto.CreateWorkspaceRequest;
import com.agenticform.dto.TransferOwnershipRequest;
import com.agenticform.dto.UpdateWorkspaceRequest;
import com.agenticform.dto.WorkspaceResponse;
import com.agenticform.dto.WorkspaceSummaryResponse;
import com.agenticform.exception.WorkspaceInvalidRoleChangeException;
import com.agenticform.exception.WorkspaceMemberNotFoundException;
import com.agenticform.model.entity.User;
import com.agenticform.model.entity.Workspace;
import com.agenticform.model.entity.WorkspaceMember;
import com.agenticform.model.entity.WorkspaceRole;
import com.agenticform.repository.UserRepository;
import com.agenticform.repository.WorkspaceMemberRepository;
import com.agenticform.repository.WorkspaceRepository;

@Service
public class WorkspaceService {

    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final UserRepository userRepository;
    private final WorkspaceAuthorizationService authorizationService;
    private final SlugService slugService;

    public WorkspaceService(
            WorkspaceRepository workspaceRepository,
            WorkspaceMemberRepository workspaceMemberRepository,
            UserRepository userRepository,
            WorkspaceAuthorizationService authorizationService,
            SlugService slugService) {
        this.workspaceRepository = workspaceRepository;
        this.workspaceMemberRepository = workspaceMemberRepository;
        this.userRepository = userRepository;
        this.authorizationService = authorizationService;
        this.slugService = slugService;
    }

    @Transactional
    public Workspace createDefaultWorkspaceForUser(User user) {
        String name = defaultWorkspaceName(user);
        return createWorkspaceForOwner(user, name, null);
    }

    @Transactional
    public WorkspaceResponse createWorkspace(Long userId, CreateWorkspaceRequest request) {
        User owner = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalStateException("User not found: id=" + userId));
        Workspace workspace = createWorkspaceForOwner(owner, request.name(), request.description());
        return toResponse(workspace, WorkspaceRole.OWNER);
    }

    @Transactional
    public Workspace createWorkspaceForOwner(User owner, String name, String description) {
        String trimmedName = name == null ? "" : name.trim();
        if (trimmedName.isEmpty()) {
            trimmedName = defaultWorkspaceName(owner);
        }

        Workspace workspace = Workspace.builder()
                .name(trimmedName)
                .slug(slugService.generateUniqueSlug(trimmedName))
                .description(normalizeDescription(description))
                .owner(owner)
                .build();

        WorkspaceMember ownerMember = WorkspaceMember.builder()
                .user(owner)
                .role(WorkspaceRole.OWNER)
                .build();
        workspace.addMember(ownerMember);

        return workspaceRepository.save(workspace);
    }

    @Transactional(readOnly = true)
    public List<WorkspaceSummaryResponse> listMyWorkspaces(Long userId) {
        return workspaceMemberRepository.findAllByUserId(userId).stream()
                .map(this::toSummary)
                .sorted(Comparator.comparing(WorkspaceSummaryResponse::name, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    @Transactional(readOnly = true)
    public WorkspaceResponse getWorkspace(Long workspaceId, Long userId) {
        WorkspaceMember membership = authorizationService.requireCanView(workspaceId, userId);
        return toResponse(membership.getWorkspace(), membership.getRole());
    }

    @Transactional
    public WorkspaceResponse updateWorkspace(Long workspaceId, Long userId, UpdateWorkspaceRequest request) {
        authorizationService.requireCanUpdateWorkspace(workspaceId, userId);
        Workspace workspace = authorizationService.requireExistingWorkspace(workspaceId);

        if (request.name() != null) {
            String trimmed = request.name().trim();
            if (!trimmed.isEmpty()) {
                workspace.setName(trimmed);
            }
        }
        if (request.description() != null) {
            workspace.setDescription(normalizeDescription(request.description()));
        }

        Workspace saved = workspaceRepository.save(workspace);
        WorkspaceRole myRole = authorizationService.getMemberRole(workspaceId, userId)
                .orElseThrow(WorkspaceMemberNotFoundException::new);
        return toResponse(saved, myRole);
    }

    @Transactional
    public void deleteWorkspace(Long workspaceId, Long userId) {
        authorizationService.requireCanDeleteWorkspace(workspaceId, userId);
        workspaceMemberRepository.deleteAll(workspaceMemberRepository.findAllByWorkspaceId(workspaceId));
        workspaceRepository.deleteById(workspaceId);
    }

    @Transactional
    public WorkspaceResponse transferOwnership(
            Long workspaceId,
            Long actorUserId,
            TransferOwnershipRequest request) {
        authorizationService.requireCanTransferOwnership(workspaceId, actorUserId);

        Long newOwnerUserId = request.newOwnerUserId();
        if (actorUserId.equals(newOwnerUserId)) {
            throw new WorkspaceInvalidRoleChangeException();
        }

        Workspace workspace = authorizationService.requireExistingWorkspace(workspaceId);

        WorkspaceMember newOwnerMember = workspaceMemberRepository
                .findByWorkspaceIdAndUserId(workspaceId, newOwnerUserId)
                .orElseThrow(WorkspaceMemberNotFoundException::new);

        if (newOwnerMember.getRole() == WorkspaceRole.OWNER) {
            throw new WorkspaceInvalidRoleChangeException();
        }

        WorkspaceMember currentOwnerMember = workspaceMemberRepository
                .findByWorkspaceIdAndUserId(workspaceId, actorUserId)
                .orElseThrow(WorkspaceMemberNotFoundException::new);

        currentOwnerMember.setRole(WorkspaceRole.ADMIN);
        newOwnerMember.setRole(WorkspaceRole.OWNER);
        workspace.setOwner(newOwnerMember.getUser());

        workspaceMemberRepository.save(currentOwnerMember);
        workspaceMemberRepository.save(newOwnerMember);
        Workspace saved = workspaceRepository.save(workspace);

        return toResponse(saved, WorkspaceRole.ADMIN);
    }

    WorkspaceResponse toResponse(Workspace workspace, WorkspaceRole myRole) {
        return new WorkspaceResponse(
                workspace.getId(),
                workspace.getName(),
                workspace.getSlug(),
                workspace.getDescription(),
                workspace.getOwner().getId(),
                myRole.name(),
                workspace.getCreatedAt(),
                workspace.getUpdatedAt());
    }

    private WorkspaceSummaryResponse toSummary(WorkspaceMember membership) {
        Workspace workspace = membership.getWorkspace();
        return new WorkspaceSummaryResponse(
                workspace.getId(),
                workspace.getName(),
                workspace.getSlug(),
                workspace.getDescription(),
                membership.getRole().name(),
                workspace.getCreatedAt());
    }

    private String defaultWorkspaceName(User user) {
        if (user.getFullName() != null && !user.getFullName().isBlank()) {
            return "Espace de " + user.getFullName().trim();
        }
        String email = user.getEmail();
        if (email == null || email.isBlank()) {
            return "Mon espace";
        }
        int at = email.indexOf('@');
        String label = at > 0 ? email.substring(0, at) : email;
        return "Espace de " + label;
    }

    private String normalizeDescription(String description) {
        if (description == null) {
            return null;
        }
        String trimmed = description.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
