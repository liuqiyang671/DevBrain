package edu.cqupt.devbrain.commerce.multimodal.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

import java.util.List;

/**
 * 导购图片上传配置属性。
 * 配置图片上传的大小限制、允许的格式和存储路径前缀。
 */
@Data
@ConfigurationProperties(prefix = "devbrain.guide.image")
public class GuideImageProperties {

    private DataSize maxFileSize = DataSize.ofMegabytes(10);

    private int maxImagesPerMessage = 4;

    private List<String> allowedContentTypes = List.of("image/jpeg", "image/png", "image/webp");

    private List<String> allowedExtensions = List.of("jpg", "jpeg", "png", "webp");

    private String objectKeyPrefix = "guide-images";
}
