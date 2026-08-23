package com.agenticform.service;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
import com.agenticform.repository.IntegrationConnectionRepository;
import com.agenticform.repository.UserRepository;
import com.agenticform.repository.WorkspaceMemberRepository;
import com.agenticform.service.GoogleCalendarIntegrationService;

@Service
public class WorkspaceMemberService {

    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final UserRepository userRepository;
    private final IntegrationConnectionRepository integrationConnectionRepository;
    private final WorkspaceAuthorizationService authorizationService;

    public WorkspaceMemberService(
            WorkspaceMemberRepository workspaceMemberRepository,
            UserRepository userRepository,
            IntegrationConnectionRepository integrationConnectionRepository,
            WorkspaceAuthorizationService authorizationService) {
        this.workspaceMemberRepository = workspaceMemberRepository;
        this.userRepository = userRepository;
        this.integrationConnectionRepository = integrationConnectionRepository;
        this.authorizationService = authorizationService;
    }

    @Transactional(readOnly = true)
    public List<WorkspaceMemberResponse> listMembers(Long workspaceId, Long userId) {
        authorizationService.requireCanView(workspaceId, userId);
        List<WorkspaceMember> members = workspaceMemberRepository.findAllByWorkspaceId(workspaceId);
        Set<Long> calendarConnectedUserIds = loadCalendarConnectedUserIds(members);
        return members.stream()
                .map((member) -> toResponse(member, calendarConnectedUserIds))
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
        Set<Long> calendarConnectedUserIds = loadCalendarConnectedUserIds(List.of(saved));
        return toResponse(saved, calendarConnectedUserIds);
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
        WorkspaceMember saved = workspaceMemberRepository.save(target);
        Set<Long> calendarConnectedUserIds = loadCalendarConnectedUserIds(List.of(saved));
        return toResponse(saved, calendarConnectedUserIds);
    }

    @Transactional
    public void removeMember(Long workspaceId, Long actorUserId, Long targetUserId) {
        authorizationService.requireCanRemoveMember(workspaceId, actorUserId, targetUserId);

        WorkspaceMember target = workspaceMemberRepository
                .findByWorkspaceIdAndUserId(workspaceId, targetUserId)
                .orElseThrow(WorkspaceMemberNotFoundException::new);

        workspaceMemberRepository.delete(target);
    }

    private Set<Long> loadCalendarConnectedUserIds(List<WorkspaceMember> members) {
        if (members.isEmpty()) {
            return Set.of();
        }
        List<Long> userIds = members.stream()
                .map((member) -> member.getUser().getId())
                .toList();
        return new HashSet<>(
                integrationConnectionRepository.findConnectedUserIds(
                        userIds,
                        GoogleCalendarIntegrationService.PROVIDER));
    }

    private WorkspaceMemberResponse toResponse(WorkspaceMember member, Set<Long> calendarConnectedUserIds) {
        User user = member.getUser();
        return new WorkspaceMemberResponse(
                member.getId(),
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                member.getRole().name(),
                member.getJoinedAt(),
                calendarConnectedUserIds.contains(user.getId()));
    }

    private String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase();
    }
}
