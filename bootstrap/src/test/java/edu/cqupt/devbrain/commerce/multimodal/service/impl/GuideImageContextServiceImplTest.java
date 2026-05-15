package edu.cqupt.devbrain.commerce.multimodal.service.impl;

import edu.cqupt.devbrain.commerce.multimodal.dao.entity.GuideImageDO;
import edu.cqupt.devbrain.commerce.multimodal.dao.mapper.GuideImageMapper;
import edu.cqupt.devbrain.commerce.multimodal.dto.GuideImageContext;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GuideImageContextServiceImplTest {

    private final GuideImageMapper guideImageMapper = mock(GuideImageMapper.class);
    private final ImageUnderstandingServiceImpl imageUnderstandingService =
            new ImageUnderstandingServiceImpl(guideImageMapper);
    private final GuideImageContextServiceImpl guideImageContextService =
            new GuideImageContextServiceImpl(guideImageMapper, imageUnderstandingService);

    @Test
    void buildContextUsesOwnedImageUnderstandingAsSupplementalText() {
        GuideImageDO image = new GuideImageDO();
        image.setId("image-1");
        image.setUserId("user-1");
        image.setFileName("activity.png");
        image.setContentType("image/png");
        image.setFileSize(128L);
        image.setPreviewUrl("/commerce/guide/images/image-1");
        image.setOcrText("限时优惠 599 元");
        image.setVisualSummary("图片可能是一张耳机活动海报。");
        image.setDetectedProductNames("[\"降噪耳机 Pro\"]");
        image.setDetectedAttributes("{\"优惠价\":\"599 元\"}");
        image.setRiskFlags("[\"识别结果来自图片，请用户确认关键信息\"]");
        when(guideImageMapper.selectBatchIds(List.of("image-1"))).thenReturn(List.of(image));

        GuideImageContext context = guideImageContextService.buildContext(List.of("image-1"), "user-1");

        assertEquals(1, context.images().size());
        assertTrue(context.contextText().contains("根据你上传的图片"));
        assertTrue(context.contextText().contains("限时优惠 599 元"));
        assertTrue(context.contextText().contains("降噪耳机 Pro"));
        assertTrue(context.contextText().contains("请用户确认关键信息"));
    }
}
