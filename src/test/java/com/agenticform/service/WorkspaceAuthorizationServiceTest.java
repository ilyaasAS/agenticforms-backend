package com.agenticform.service;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.BDDMockito.given;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.agenticform.exception.WorkspaceAccessDeniedException;
import com.agenticform.model.entity.User;
import com.agenticform.model.entity.Workspace;
import com.agenticform.model.entity.WorkspaceMember;
import com.agenticform.model.entity.WorkspaceRole;
import com.agenticform.repository.WorkspaceMemberRepository;
import com.agenticform.repository.WorkspaceRepository;

@ExtendWith(MockitoExtension.class)
class WorkspaceAuthorizationServiceTest {

    @Mock
    private WorkspaceRepository workspaceRepository;

    @Mock
    private WorkspaceMemberRepository workspaceMemberRepository;

    @InjectMocks
    private WorkspaceAuthorizationService authorizationService;

    @Test
    void memberCannotUpdateWorkspace() {
        Workspace workspace = new Workspace();
        workspace.setId(10L);
        User user = new User();
        user.setId(2L);
        WorkspaceMember member = WorkspaceMember.builder()
                .workspace(workspace)
                .user(user)
                .role(WorkspaceRole.MEMBER)
                .build();

        given(workspaceRepository.findById(10L)).willReturn(Optional.of(workspace));
        given(workspaceMemberRepository.findByWorkspaceIdAndUserId(10L, 2L)).willReturn(Optional.of(member));

        assertThrows(
                WorkspaceAccessDeniedException.class,
                () -> authorizationService.requireCanUpdateWorkspace(10L, 2L));
    }
}
