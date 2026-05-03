package edu.cqupt.devbrain.knowledge.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 对象存储配置属性 —— 绑定 devbrain.object-storage 配置段。
 * <p>
 * 支持 S3 兼容存储（AWS S3、MinIO 等），通过 endpoint 和 bucket 配置连接。
 */
@Data
@ConfigurationProperties(prefix = "devbrain.object-storage")
public class ObjectStorageProperties {

    /** 存储提供商标识，如 minio、aws。 */
    private String provider = "minio";

    /** S3 API 端点，MinIO 场景为 http://host:port。 */
    private String endpoint = "http://localhost:9000";

    /** 外部可访问的端点，用于生成文件 URL。 */
    private String externalEndpoint = "http://localhost:9000";

    /** AWS 区域，MinIO 场景可为任意值。 */
    private String region = "us-east-1";

    /** 存储桶名称。 */
    private String bucket = "devbrain";

    /** 访问密钥 ID。 */
    private String accessKey = "";

    /** 访问密钥 Secret。 */
    private String secretKey = "";
}
