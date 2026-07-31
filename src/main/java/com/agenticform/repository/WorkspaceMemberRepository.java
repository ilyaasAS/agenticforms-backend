package com.agenticform.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.agenticform.model.entity.WorkspaceMember;
import com.agenticform.model.entity.WorkspaceRole;

public interface WorkspaceMemberRepository extends JpaRepository<WorkspaceMember, Long> {

    Optional<WorkspaceMember> findByWorkspaceIdAndUserId(Long workspaceId, Long userId);

    List<WorkspaceMember> findAllByUserId(Long userId);

    List<WorkspaceMember> findAllByWorkspaceId(Long workspaceId);

    boolean existsByWorkspaceIdAndUserId(Long workspaceId, Long userId);

    long countByWorkspaceIdAndRole(Long workspaceId, WorkspaceRole role);
}
