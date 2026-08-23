package com.agenticform.controller;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.agenticform.dto.MediaUploadResponse;
import com.agenticform.exception.GlobalExceptionHandler;
import com.agenticform.service.ImageResolveService;
import com.agenticform.service.MediaStorageService;

@ExtendWith(MockitoExtension.class)
class MediaUploadAuthTest {

    private MockMvc mockMvc;

    @Mock
    private MediaStorageService mediaStorageService;

    @Mock
    private ImageResolveService imageResolveService;

    @InjectMocks
    private MediaController mediaController;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(mediaController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void securityConfigNoLongerPermitsAnonymousUpload() throws Exception {
        String source = Files.readString(
                Path.of("src/main/java/com/agenticform/config/SecurityConfig.java"));
        assertFalse(
                source.contains("/api/v1/media/upload\").permitAll()"),
                "POST /api/v1/media/upload must require authentication");
    }

    @Test
    void uploadReturnsCreatedWhenFileIsAccepted() throws Exception {
        given(mediaStorageService.store(any())).willReturn(
                new MediaUploadResponse("/v1/media/files/cover.png", "image/png", 4));

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "cover.png",
                "image/png",
                new byte[] { 1, 2, 3, 4 });

        mockMvc.perform(multipart("/api/v1/media/upload").file(file))
                .andExpect(status().isCreated());
    }
}
