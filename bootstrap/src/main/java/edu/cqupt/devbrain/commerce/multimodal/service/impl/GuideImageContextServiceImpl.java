package edu.cqupt.devbrain.commerce.multimodal.service.impl;

import edu.cqupt.devbrain.commerce.multimodal.dao.entity.GuideImageDO;
import edu.cqupt.devbrain.commerce.multimodal.dao.mapper.GuideImageMapper;
import edu.cqupt.devbrain.commerce.multimodal.dto.GuideImageContext;
import edu.cqupt.devbrain.commerce.multimodal.dto.GuideImageRef;
import edu.cqupt.devbrain.commerce.multimodal.dto.ImageUnderstandingResult;
import edu.cqupt.devbrain.commerce.multimodal.service.GuideImageContextService;
import edu.cqupt.devbrain.commerce.multimodal.service.ImageUnderstandingService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 导购图片上下文服务实现类。
 * 批量读取图片记录并调用视觉分析，将分析结果聚合为文本上下文注入导购对话。
 * 分析失败时降级为文件元数据提示。
 */
@Service
@RequiredArgsConstructor
public class GuideImageContextServiceImpl implements GuideImageContextService {

    private final GuideImageMapper guideImageMapper;
    private final ImageUnderstandingService imageUnderstandingService;

    @Override
    public GuideImageContext buildContext(List<String> imageIds, String userId) {
        if (imageIds == null || imageIds.isEmpty()) {
            return new GuideImageContext(List.of(), "");
        }
        List<GuideImageDO> images = guideImageMapper.selectBatchIds(imageIds).stream()
                .filter(Objects::nonNull)
                .filter(image -> !Integer.valueOf(1).equals(image.getDeleted()))
                .filter(image -> !StringUtils.hasText(userId) || userId.equals(image.getUserId()))
                .toList();
        List<GuideImageRef> refs = new ArrayList<>();
        StringBuilder context = new StringBuilder("根据你上传的图片，我会把以下信息作为补充上下文，但关键价格、优惠和参数仍建议你确认：");
        for (GuideImageDO image : images) {
            ImageUnderstandingResult result = safeAnalyze(image);
            GuideImageRef ref = toRef(image, result);
            refs.add(ref);
            appendContext(context, ref);
        }
        return new GuideImageContext(refs, refs.isEmpty() ? "" : context.toString());
    }

    private ImageUnderstandingResult safeAnalyze(GuideImageDO image) {
        try {
            return imageUnderstandingService.analyze(image.getId());
        } catch (RuntimeException ignored) {
            List<String> existingRiskFlags = GuideImageJsonSupport.readStringList(image.getRiskFlags());
            return new ImageUnderstandingResult(
                    image.getId(),
                    image.getOcrText(),
                    image.getVisualSummary(),
                    GuideImageJsonSupport.readStringList(image.getDetectedProductNames()),
                    GuideImageJsonSupport.readStringMap(image.getDetectedAttributes()),
                    existingRiskFlags.isEmpty() ? List.of("图片识别失败，请用户确认图片中的关键信息") : existingRiskFlags,
                    0D
            );
        }
    }

    private GuideImageRef toRef(GuideImageDO image, ImageUnderstandingResult result) {
        return new GuideImageRef(
                image.getId(),
                image.getFileName(),
                image.getPreviewUrl(),
                result.ocrText(),
                result.visualSummary(),
                result.detectedProductNames(),
                result.detectedAttributes(),
                result.riskFlags()
        );
    }

    private void appendContext(StringBuilder context, GuideImageRef ref) {
        context.append("\n[image:").append(ref.imageId()).append("] ")
                .append(ref.fileName()).append("。");
        if (StringUtils.hasText(ref.visualSummary())) {
            context.append("图片摘要：").append(ref.visualSummary()).append("。");
        }
        if (StringUtils.hasText(ref.ocrText())) {
            context.append("OCR：").append(ref.ocrText()).append("。");
        }
        if (!ref.detectedProductNames().isEmpty()) {
            context.append("识别商品：").append(String.join("、", ref.detectedProductNames())).append("。");
        }
        if (!ref.detectedAttributes().isEmpty()) {
            context.append("识别属性：");
            for (Map.Entry<String, String> entry : ref.detectedAttributes().entrySet()) {
                context.append(entry.getKey()).append("=").append(entry.getValue()).append("；");
            }
        }
        if (!ref.riskFlags().isEmpty()) {
            context.append("风险提示：").append(String.join("；", ref.riskFlags())).append("。");
        }
    }
}
