package edu.cqupt.devbrain.knowledge.storage;

import java.io.InputStream;

/**
 * 文件存储服务接口 —— 抽象对象存储操作，屏蔽底层 S3/MinIO 实现差异。
 */
public interface FileStorageService {

    /**
     * 上传文件到对象存储。
     *
     * @param objectKey  对象 key，不含 bucket 前缀
     * @param inputStream 文件输入流
     * @param contentType MIME 类型，可为空
     * @param size        文件大小（字节），可为 -1 表示未知
     * @return 上传后的文件访问 URL
     */
    String upload(String objectKey, InputStream inputStream, String contentType, long size);

    /**
     * 删除对象存储中的文件。
     *
     * @param objectKey 对象 key
     */
    void delete(String objectKey);
}
