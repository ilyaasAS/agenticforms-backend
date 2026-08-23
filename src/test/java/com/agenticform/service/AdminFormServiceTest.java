package com.agenticform.service;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.agenticform.model.entity.Form;
import com.agenticform.model.entity.FormStatus;
import com.agenticform.model.entity.Workspace;
import com.agenticform.repository.FormRepository;

@ExtendWith(MockitoExtension.class)
class AdminFormServiceTest {

    @Mock
    private FormRepository formRepository;

    @InjectMocks
    private AdminFormService adminFormService;

    @Test
    void setBlockedMarksFormUnavailable() {
        Workspace workspace = new Workspace();
        workspace.setId(4L);
        workspace.setName("Ada");
        Form form = new Form();
        form.setId(12L);
        form.setTitle("Sondage");
        form.setStatus(FormStatus.PUBLISHED);
        form.setWorkspace(workspace);
        given(formRepository.findById(12L)).willReturn(Optional.of(form));
        given(formRepository.save(any(Form.class))).willAnswer(invocation -> invocation.getArgument(0));

        var updated = adminFormService.setBlocked(12L, true);

        assertTrue(updated.blocked());
        verify(formRepository).save(form);
    }
}
