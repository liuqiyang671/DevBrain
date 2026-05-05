package edu.cqupt.devbrain.ingestion.domain.context;

import edu.cqupt.devbrain.ingestion.domain.SourceType;
import lombok.Builder;
import lombok.Data;

import java.util.HashMap;
import java.util.Map;

/**
 * 文档来源信息，描述 fetcher 节点如何定位和访问原始文档。
 */
@Data
@Builder
public class DocumentSource {

    /**
     * 来源类型，如本地文件、URL、飞书或 S3。
     */
    private SourceType type;

    /**
     * 文件路径、URL、对象存储 key 或第三方文档标识。
     */
    private String location;

    /**
     * 原始文件名，用于保留扩展名和展示名称。
     */
    private String fileName;

    /**
     * 访问来源所需的认证信息。只在运行时使用，不应持久化真实密钥。
     */
    @Builder.Default
    private Map<String, String> credentials = new HashMap<>();
}
