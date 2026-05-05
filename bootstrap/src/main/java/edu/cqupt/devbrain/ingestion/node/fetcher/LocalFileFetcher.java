package edu.cqupt.devbrain.ingestion.node.fetcher;

import edu.cqupt.devbrain.ingestion.domain.SourceType;
import edu.cqupt.devbrain.ingestion.domain.context.DocumentSource;
import edu.cqupt.devbrain.knowledge.storage.FileStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.io.InputStream;

/**
 * 本地/已上传文件获取策略，通过项目现有 FileStorageService 读取对象存储中的文件内容。
 */
@Component
@RequiredArgsConstructor
public class LocalFileFetcher implements DocumentFetcher {

    /**
     * 复用知识库文件存储抽象，屏蔽本地文件、MinIO 或 S3 兼容存储差异。
     */
    private final FileStorageService fileStorageService;

    /**
     * 从对象存储下载文件输入流，并一次性读取为字节数组供后续 ParserNode 使用。
     */
    @Override
    public byte[] fetch(DocumentSource source) throws Exception {
        try (InputStream inputStream = fileStorageService.download(source.getLocation())) {
            return inputStream.readAllBytes();
        }
    }

    /**
     * 当前策略处理 FILE 来源。
     */
    @Override
    public SourceType getSupportedType() {
        return SourceType.FILE;
    }
}
