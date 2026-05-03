package edu.cqupt.devbrain.knowledge.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

import java.util.List;

/**
 * 文件上传配置属性 —— 集中管理上传模块的可配置参数。
 * <p>
 * 配置前缀：{@code devbrain.upload}，所有属性均可在 application.yml 中覆盖。
 * Java 默认值作为兜底，yaml 作为显式配置。
 */
@Data
@ConfigurationProperties(prefix = "devbrain.upload")
public class UploadProperties {

    /** 单文件大小上限，默认 50MB。 */
    private DataSize maxFileSize = DataSize.ofMegabytes(50);

    /** 允许上传的文件扩展名白名单（小写）。 */
    private List<String> allowedExtensions = List.of(
            "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx",
            "md", "txt", "csv", "json", "html", "htm", "xml"
    );

    /** 禁止上传的文件扩展名黑名单（小写），优先级高于白名单。 */
    private List<String> blockedExtensions = List.of(
            "exe", "sh", "bat", "cmd", "jsp", "php", "jar", "class", "dll", "so"
    );
}
