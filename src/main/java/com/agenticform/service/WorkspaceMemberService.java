package com.agenticform.service;

import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.agenticform.dto.AddMemberRequest;
import com.agenticform.dto.UpdateMemberRoleRequest;
import com.agenticform.dto.WorkspaceMemberResponse;
import com.agenticform.exception.UserNotFoundByEmailException;
import com.agenticform.exception.WorkspaceInvalidRoleChangeException;
import com.agenticform.exception.WorkspaceMemberAlreadyExistsException;
import com.agenticform.exception.WorkspaceMemberNotFoundException;
import com.agenticform.model.entity.User;
import com.agenticform.model.entity.Workspace;
import com.agenticform.model.entity.WorkspaceMember;
import com.agenticform.model.entity.WorkspaceRole;
import com.agenticform.repository.UserRepository;
import com.agenticform.repository.WorkspaceMemberRepository;

@Service
public class WorkspaceMemberService {

    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final UserRepository userRepository;
    private final WorkspaceAuthorizationService authorizationService;

    public WorkspaceMemberService(
            WorkspaceMemberRepository workspaceMemberRepository,
            UserRepository userRepository,
            WorkspaceAuthorizationService authorizationService) {
        this.workspaceMemberRepository = workspaceMemberRepository;
        this.userRepository = userRepository;
        this.authorizationService = authorizationService;
    }

    @Transactional(readOnly = true)
    public List<WorkspaceMemberResponse> listMembers(Long workspaceId, Long userId) {
        authorizationService.requireCanView(workspaceId, userId);
        return workspaceMemberRepository.findAllByWorkspaceId(workspaceId).stream()
                .map(this::toResponse)
                .sorted(Comparator.comparing(WorkspaceMemberResponse::email, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    @Transactional
    public WorkspaceMemberResponse addMember(Long workspaceId, Long actorUserId, AddMemberRequest request) {
        authorizationService.requireCanManageMembers(workspaceId, actorUserId);

        WorkspaceRole role = request.role() != null ? request.role() : WorkspaceRole.MEMBER;
        if (role == WorkspaceRole.OWNER) {
            throw new WorkspaceInvalidRoleChangeException();
        }

        String email = normalizeEmail(request.email());
        User invitee = userRepository.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundByEmailException(email));

        if (workspaceMemberRepository.existsByWorkspaceIdAndUserId(workspaceId, invitee.getId())) {
            throw new WorkspaceMemberAlreadyExistsException();
        }

        Workspace workspace = authorizationService.requireExistingWorkspace(workspaceId);
        WorkspaceMember member = WorkspaceMember.builder()
                .user(invitee)
                .role(role)
                .build();
        workspace.addMember(member);

        WorkspaceMember saved = workspaceMemberRepository.save(member);
        return toResponse(saved);
    }

    @Transactional
    public WorkspaceMemberResponse updateMemberRole(
            Long workspaceId,
            Long actorUserId,
            Long targetUserId,
            UpdateMemberRoleRequest request) {
        authorizationService.requireCanChangeMemberRole(
                workspaceId, actorUserId, targetUserId, request.role());

        WorkspaceMember target = workspaceMemberRepository
                .findByWorkspaceIdAndUserId(workspaceId, targetUserId)
                .orElseThrow(WorkspaceMemberNotFoundException::new);

        target.setRole(request.role());
        return toResponse(workspaceMemberRepository.save(target));
    }

    @Transactional
    public void removeMember(Long workspaceId, Long actorUserId, Long targetUserId) {
        authorizationService.requireCanRemoveMember(workspaceId, actorUserId, targetUserId);

        WorkspaceMember target = workspaceMemberRepository
                .findByWorkspaceIdAndUserId(workspaceId, targetUserId)
                .orElseThrow(WorkspaceMemberNotFoundException::new);

        workspaceMemberRepository.delete(target);
    }

    private WorkspaceMemberResponse toResponse(WorkspaceMember member) {
        User user = member.getUser();
        return new WorkspaceMemberResponse(
                member.getId(),
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                member.getRole().name(),
                member.getJoinedAt());
    }

    private String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase();
    }
}
