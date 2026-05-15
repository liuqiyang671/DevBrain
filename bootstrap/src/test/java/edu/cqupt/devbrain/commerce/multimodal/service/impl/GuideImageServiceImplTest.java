package edu.cqupt.devbrain.commerce.multimodal.service.impl;

import edu.cqupt.devbrain.commerce.multimodal.config.GuideImageProperties;
import edu.cqupt.devbrain.commerce.multimodal.dao.entity.GuideImageDO;
import edu.cqupt.devbrain.commerce.multimodal.dao.mapper.GuideImageMapper;
import edu.cqupt.devbrain.commerce.multimodal.dto.GuideImageUploadResp;
import edu.cqupt.devbrain.framework.exception.ClientException;
import edu.cqupt.devbrain.knowledge.storage.FileStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.util.unit.DataSize;

import java.io.InputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GuideImageServiceImplTest {

    private final GuideImageMapper guideImageMapper = mock(GuideImageMapper.class);
    private final FileStorageService fileStorageService = mock(FileStorageService.class);
    private GuideImageServiceImpl guideImageService;

    @BeforeEach
    void setUp() {
        GuideImageProperties properties = new GuideImageProperties();
        properties.setMaxFileSize(DataSize.ofMegabytes(10));
        properties.setAllowedContentTypes(List.of("image/jpeg", "image/png", "image/webp"));
        properties.setAllowedExtensions(List.of("jpg", "jpeg", "png", "webp"));
        guideImageService = new GuideImageServiceImpl(guideImageMapper, fileStorageService, properties);
    }

    @Test
    void uploadRejectsUnsupportedContentTypeBeforeStorage() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "note.txt", "text/plain", "not-image".getBytes());

        assertThrows(ClientException.class, () -> guideImageService.upload(file, "session-1", "user-1"));

        verify(fileStorageService, never()).upload(any(), any(), any(), anyLong());
        verify(guideImageMapper, never()).insert(any(GuideImageDO.class));
    }

    @Test
    void uploadStoresImageAndReturnsStableReference() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "screenshot.png", "image/png", new byte[]{1, 2, 3, 4});
        when(fileStorageService.upload(any(), any(InputStream.class), eq("image/png"), eq(4L)))
                .thenReturn("http://localhost:9000/devbrain/guide-images/user-1/screenshot.png");

        GuideImageUploadResp response = guideImageService.upload(file, "session-1", "user-1");

        assertNotNull(response.imageId());
        assertEquals("screenshot.png", response.fileName());
        assertEquals("image/png", response.contentType());
        assertEquals(4L, response.size());
        assertTrue(response.previewUrl().contains("/commerce/guide/images/" + response.imageId()));

        ArgumentCaptor<GuideImageDO> captor = ArgumentCaptor.forClass(GuideImageDO.class);
        verify(guideImageMapper).insert(captor.capture());
        GuideImageDO saved = captor.getValue();
        assertEquals(response.imageId(), saved.getId());
        assertEquals("user-1", saved.getUserId());
        assertEquals("session-1", saved.getSessionId());
        assertEquals("screenshot.png", saved.getFileName());
        assertTrue(saved.getObjectKey().startsWith("guide-images/user-1/"));
    }
}
