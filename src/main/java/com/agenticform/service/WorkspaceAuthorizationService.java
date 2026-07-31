package com.agenticform.service;

import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.agenticform.exception.WorkspaceAccessDeniedException;
import com.agenticform.exception.WorkspaceInvalidRoleChangeException;
import com.agenticform.exception.WorkspaceLastOwnerException;
import com.agenticform.exception.WorkspaceMemberNotFoundException;
import com.agenticform.exception.WorkspaceNotFoundException;
import com.agenticform.model.entity.Workspace;
import com.agenticform.model.entity.WorkspaceMember;
import com.agenticform.model.entity.WorkspaceRole;
import com.agenticform.repository.WorkspaceMemberRepository;
import com.agenticform.repository.WorkspaceRepository;

/**
 * Vérification des droits workspace (RBAC) — distinct des rôles plateforme Spring Security.
 * <p>
 * Matrice :
 * <ul>
 *   <li>MEMBER : lecture workspace + liste membres</li>
 *   <li>ADMIN : + modification workspace, gestion membres (sauf OWNER)</li>
 *   <li>OWNER : + suppression workspace, transfert propriété</li>
 * </ul>
 */
@Service
public class WorkspaceAuthorizationService {

    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;

    public WorkspaceAuthorizationService(
            WorkspaceRepository workspaceRepository,
            WorkspaceMemberRepository workspaceMemberRepository) {
        this.workspaceRepository = workspaceRepository;
        this.workspaceMemberRepository = workspaceMemberRepository;
    }

    @Transactional(readOnly = true)
    public Workspace requireExistingWorkspace(Long workspaceId) {
        return workspaceRepository.findById(workspaceId)
                .orElseThrow(() -> new WorkspaceNotFoundException(workspaceId));
    }

    @Transactional(readOnly = true)
    public Optional<WorkspaceRole> getMemberRole(Long workspaceId, Long userId) {
        if (workspaceId == null || userId == null) {
            return Optional.empty();
        }
        return workspaceMemberRepository.findByWorkspaceIdAndUserId(workspaceId, userId)
                .map(WorkspaceMember::getRole);
    }

    @Transactional(readOnly = true)
    public WorkspaceMember requireMembership(Long workspaceId, Long userId) {
        requireExistingWorkspace(workspaceId);
        return workspaceMemberRepository.findByWorkspaceIdAndUserId(workspaceId, userId)
                .orElseThrow(WorkspaceAccessDeniedException::new);
    }

    @Transactional(readOnly = true)
    public WorkspaceMember requireRole(Long workspaceId, Long userId, WorkspaceRole minimumRole) {
        WorkspaceMember member = requireMembership(workspaceId, userId);
        if (!member.getRole().isAtLeast(minimumRole)) {
            throw new WorkspaceAccessDeniedException();
        }
        return member;
    }

    /** MEMBER+ : consulter le workspace et ses membres. */
    @Transactional(readOnly = true)
    public WorkspaceMember requireCanView(Long workspaceId, Long userId) {
        return requireMembership(workspaceId, userId);
    }

    /** ADMIN+ : modifier le workspace (nom, description). */
    @Transactional(readOnly = true)
    public WorkspaceMember requireCanUpdateWorkspace(Long workspaceId, Long userId) {
        return requireRole(workspaceId, userId, WorkspaceRole.ADMIN);
    }

    /** ADMIN+ : inviter ou retirer des membres (sous contraintes OWNER). */
    @Transactional(readOnly = true)
    public WorkspaceMember requireCanManageMembers(Long workspaceId, Long userId) {
        return requireRole(workspaceId, userId, WorkspaceRole.ADMIN);
    }

    /** OWNER : supprimer le workspace. */
    @Transactional(readOnly = true)
    public WorkspaceMember requireCanDeleteWorkspace(Long workspaceId, Long userId) {
        return requireRole(workspaceId, userId, WorkspaceRole.OWNER);
    }

    /** OWNER : transférer la propriété. */
    @Transactional(readOnly = true)
    public WorkspaceMember requireCanTransferOwnership(Long workspaceId, Long userId) {
        return requireRole(workspaceId, userId, WorkspaceRole.OWNER);
    }

    /**
     * ADMIN+ peut modifier le rôle d'un membre, sauf :
     * <ul>
     *   <li>touché à l'OWNER actuel</li>
     *   <li>promotion en OWNER (réservée au transfert dédié)</li>
     * </ul>
     */
    @Transactional(readOnly = true)
    public void requireCanChangeMemberRole(
            Long workspaceId,
            Long actorUserId,
            Long targetUserId,
            WorkspaceRole newRole) {
        requireCanManageMembers(workspaceId, actorUserId);

        if (newRole == WorkspaceRole.OWNER) {
            throw new WorkspaceInvalidRoleChangeException();
        }

        WorkspaceMember target = workspaceMemberRepository
                .findByWorkspaceIdAndUserId(workspaceId, targetUserId)
                .orElseThrow(WorkspaceMemberNotFoundException::new);

        if (target.getRole() == WorkspaceRole.OWNER) {
            throw new WorkspaceInvalidRoleChangeException();
        }
    }

    /**
     * ADMIN+ peut retirer un membre, sauf l'OWNER.
     * Un membre peut se retirer lui-même (quit), sauf s'il est le seul OWNER.
     */
    @Transactional(readOnly = true)
    public void requireCanRemoveMember(Long workspaceId, Long actorUserId, Long targetUserId) {
        WorkspaceMember target = workspaceMemberRepository
                .findByWorkspaceIdAndUserId(workspaceId, targetUserId)
                .orElseThrow(WorkspaceMemberNotFoundException::new);

        if (actorUserId.equals(targetUserId)) {
            if (target.getRole() == WorkspaceRole.OWNER) {
                long ownerCount = workspaceMemberRepository.countByWorkspaceIdAndRole(
                        workspaceId, WorkspaceRole.OWNER);
                if (ownerCount <= 1) {
                    throw new WorkspaceLastOwnerException();
                }
            }
            return;
        }

        requireCanManageMembers(workspaceId, actorUserId);

        if (target.getRole() == WorkspaceRole.OWNER) {
            throw new WorkspaceInvalidRoleChangeException();
        }
    }

    @Transactional(readOnly = true)
    public boolean isMember(Long workspaceId, Long userId) {
        return workspaceMemberRepository.existsByWorkspaceIdAndUserId(workspaceId, userId);
    }

    @Transactional(readOnly = true)
    public boolean hasRoleAtLeast(Long workspaceId, Long userId, WorkspaceRole minimumRole) {
        return getMemberRole(workspaceId, userId)
                .map(role -> role.isAtLeast(minimumRole))
                .orElse(false);
    }
}
