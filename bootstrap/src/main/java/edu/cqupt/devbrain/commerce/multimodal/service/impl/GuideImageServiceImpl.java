package edu.cqupt.devbrain.commerce.multimodal.service.impl;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import edu.cqupt.devbrain.commerce.multimodal.config.GuideImageProperties;
import edu.cqupt.devbrain.commerce.multimodal.dao.entity.GuideImageDO;
import edu.cqupt.devbrain.commerce.multimodal.dao.mapper.GuideImageMapper;
import edu.cqupt.devbrain.commerce.multimodal.dto.GuideImageUploadResp;
import edu.cqupt.devbrain.commerce.multimodal.service.GuideImageService;
import edu.cqupt.devbrain.framework.exception.ClientException;
import edu.cqupt.devbrain.knowledge.storage.FileStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.time.Instant;
import java.util.Locale;
import java.util.Set;

/**
 * 导购图片服务实现类。
 * 负责图片的上传校验（格式、大小、扩展名安全检查）、对象存储和元数据持久化。
 */
@Service
@RequiredArgsConstructor
public class GuideImageServiceImpl implements GuideImageService {

    private static final Set<String> DANGEROUS_EXTENSIONS = Set.of("exe", "sh", "bat", "cmd", "jsp", "php", "jar");

    private final GuideImageMapper guideImageMapper;
    private final FileStorageService fileStorageService;
    private final GuideImageProperties properties;

    @Override
    public GuideImageUploadResp upload(MultipartFile file, String sessionId, String userId) {
        validate(file);
        String fileName = sanitize(file.getOriginalFilename());
        String extension = extension(fileName);
        String imageId = IdUtil.getSnowflakeNextIdStr();
        String safeUser = StringUtils.hasText(userId) ? userId : "anonymous";
        String objectKey = cleanPrefix() + "/" + safeUser + "/" + imageId + "." + extension;
        try (InputStream inputStream = file.getInputStream()) {
            fileStorageService.upload(objectKey, inputStream, file.getContentType(), file.getSize());
        } catch (Exception ex) {
            if (ex instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new ClientException("图片上传失败：" + ex.getMessage());
        }

        GuideImageDO image = new GuideImageDO();
        image.setId(imageId);
        image.setUserId(userId);
        image.setSessionId(clean(sessionId));
        image.setFileName(fileName);
        image.setContentType(file.getContentType());
        image.setFileSize(file.getSize());
        image.setObjectKey(objectKey);
        image.setPreviewUrl(previewUrl(imageId));
        image.setAnalyzeStatus("pending");
        image.setDetectedProductNames("[]");
        image.setDetectedAttributes("{}");
        image.setRiskFlags("[]");
        image.setCreatedBy(userId);
        image.setUpdatedBy(userId);
        guideImageMapper.insert(image);
        return toUploadResp(image);
    }

    @Override
    public GuideImageUploadResp get(String imageId, String userId) {
        return toUploadResp(getOwnedImage(imageId, userId));
    }

    @Override
    public GuideImageDO getOwnedImage(String imageId, String userId) {
        if (!StringUtils.hasText(imageId)) {
            throw new ClientException("图片 ID 不能为空");
        }
        GuideImageDO image = guideImageMapper.selectOne(Wrappers.lambdaQuery(GuideImageDO.class)
                .eq(GuideImageDO::getId, imageId)
                .eq(StringUtils.hasText(userId), GuideImageDO::getUserId, userId)
                .eq(GuideImageDO::getDeleted, 0)
                .last("LIMIT 1"));
        if (image == null) {
            throw new ClientException("图片不存在或无权访问");
        }
        return image;
    }

    @Override
    public InputStream download(String imageId, String userId) {
        GuideImageDO image = getOwnedImage(imageId, userId);
        return fileStorageService.download(image.getObjectKey());
    }

    private void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ClientException("上传图片不能为空");
        }
        if (!StringUtils.hasText(file.getOriginalFilename())) {
            throw new ClientException("图片文件名不能为空");
        }
        if (file.getSize() <= 0) {
            throw new ClientException("图片大小必须大于 0");
        }
        if (file.getSize() > properties.getMaxFileSize().toBytes()) {
            throw new ClientException("图片大小超过限制，最大允许 " + properties.getMaxFileSize().toMegabytes() + "MB");
        }
        String contentType = file.getContentType();
        if (!StringUtils.hasText(contentType) || !properties.getAllowedContentTypes().contains(contentType.toLowerCase(Locale.ROOT))) {
            throw new ClientException("仅支持上传 JPG、PNG 或 WebP 图片");
        }
        String extension = extension(file.getOriginalFilename());
        if (DANGEROUS_EXTENSIONS.contains(extension) || !properties.getAllowedExtensions().contains(extension)) {
            throw new ClientException("仅支持上传 jpg、jpeg、png、webp 图片");
        }
    }

    private GuideImageUploadResp toUploadResp(GuideImageDO image) {
        return new GuideImageUploadResp(
                image.getId(),
                image.getFileName(),
                image.getContentType(),
                image.getFileSize(),
                image.getPreviewUrl(),
                image.getOcrText(),
                image.getVisualSummary(),
                GuideImageJsonSupport.readStringList(image.getDetectedProductNames()),
                GuideImageJsonSupport.readStringMap(image.getDetectedAttributes()),
                GuideImageJsonSupport.readStringList(image.getRiskFlags()),
                image.getCreateTime() == null ? Instant.now() : image.getCreateTime().toInstant()
        );
    }

    private String sanitize(String fileName) {
        String sanitized = fileName == null ? "" : fileName;
        int lastSlash = Math.max(sanitized.lastIndexOf('/'), sanitized.lastIndexOf('\\'));
        if (lastSlash >= 0) {
            sanitized = sanitized.substring(lastSlash + 1);
        }
        sanitized = sanitized.replace("\0", "").trim();
        if (!StringUtils.hasText(sanitized)) {
            throw new ClientException("图片文件名不能为空");
        }
        return sanitized;
    }

    private String extension(String fileName) {
        if (!StringUtils.hasText(fileName)) {
            return "";
        }
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private String previewUrl(String imageId) {
        return "/commerce/guide/images/" + imageId + "/content";
    }

    private String cleanPrefix() {
        String prefix = properties.getObjectKeyPrefix();
        return StringUtils.hasText(prefix) ? prefix.replaceAll("^/+|/+$", "") : "guide-images";
    }

    private String clean(String value) {
        return value == null ? null : value.trim();
    }
}
