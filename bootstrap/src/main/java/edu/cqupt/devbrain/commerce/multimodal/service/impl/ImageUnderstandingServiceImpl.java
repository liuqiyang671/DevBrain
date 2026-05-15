package edu.cqupt.devbrain.commerce.multimodal.service.impl;

import edu.cqupt.devbrain.commerce.multimodal.dao.entity.GuideImageDO;
import edu.cqupt.devbrain.commerce.multimodal.dao.mapper.GuideImageMapper;
import edu.cqupt.devbrain.commerce.multimodal.dto.ImageUnderstandingResult;
import edu.cqupt.devbrain.commerce.multimodal.service.ImageUnderstandingService;
import edu.cqupt.devbrain.framework.exception.ClientException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;

/**
 * 图片理解服务实现类。
 * 当前为降级实现，未配置视觉模型时返回文件元数据和提示信息。
 * 配置视觉模型后可扩展为真正的AI图片分析。
 */
@Service
@RequiredArgsConstructor
public class ImageUnderstandingServiceImpl implements ImageUnderstandingService {

    private final GuideImageMapper guideImageMapper;

    @Override
    public ImageUnderstandingResult analyze(String imageId) {
        GuideImageDO image = guideImageMapper.selectById(imageId);
        if (image == null || Integer.valueOf(1).equals(image.getDeleted())) {
            throw new ClientException("图片不存在或已删除");
        }
        ImageUnderstandingResult result = currentOrFallback(image);
        image.setOcrText(result.ocrText());
        image.setVisualSummary(result.visualSummary());
        image.setDetectedProductNames(GuideImageJsonSupport.writeList(result.detectedProductNames()));
        image.setDetectedAttributes(GuideImageJsonSupport.writeMap(result.detectedAttributes()));
        image.setRiskFlags(GuideImageJsonSupport.writeList(result.riskFlags()));
        image.setAnalyzeStatus("completed");
        guideImageMapper.updateById(image);
        return result;
    }

    private ImageUnderstandingResult currentOrFallback(GuideImageDO image) {
        List<String> riskFlags = GuideImageJsonSupport.readStringList(image.getRiskFlags());
        if (riskFlags.isEmpty()) {
            riskFlags = List.of("当前未配置视觉模型，图片理解结果仅包含文件元数据，请用户确认关键信息");
        }
        String summary = StringUtils.hasText(image.getVisualSummary())
                ? image.getVisualSummary()
                : "用户上传了图片 " + image.getFileName() + "，系统已保存图片引用，暂未执行视觉内容识别。";
        return new ImageUnderstandingResult(
                image.getId(),
                emptyToNull(image.getOcrText()),
                summary,
                GuideImageJsonSupport.readStringList(image.getDetectedProductNames()),
                GuideImageJsonSupport.readStringMap(image.getDetectedAttributes()),
                riskFlags,
                StringUtils.hasText(image.getVisualSummary()) ? 0.55D : 0.1D
        );
    }

    private String emptyToNull(String value) {
        return StringUtils.hasText(value) ? value : null;
    }
}
