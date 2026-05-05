package edu.cqupt.devbrain.knowledge.storage;

import edu.cqupt.devbrain.framework.errorcode.BaseErrorCode;
import edu.cqupt.devbrain.framework.exception.ServiceException;
import edu.cqupt.devbrain.knowledge.config.ObjectStorageProperties;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.InputStream;
import java.net.URI;

/**
 * S3 兼容对象存储实现 —— 支持 AWS S3 和 MinIO。
 * <p>
 * 使用 AWS SDK v2 流式上传，不在内存中缓存完整文件。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class S3FileStorageService implements FileStorageService {

    private final ObjectStorageProperties properties;
    private S3Client s3Client;

    /**
     * 初始化 S3 客户端，读取对象存储配置并构建 S3Client 实例。
     */
    @PostConstruct
    public void init() {
        this.s3Client = S3Client.builder()
                .endpointOverride(URI.create(properties.getEndpoint()))
                .region(Region.of(properties.getRegion()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(properties.getAccessKey(), properties.getSecretKey())))
                .forcePathStyle(true)
                .build();
        log.info("S3 客户端初始化完成，endpoint={}, bucket={}", properties.getEndpoint(), properties.getBucket());
    }

    /**
     * 应用关闭时释放 S3 客户端资源。
     */
    @PreDestroy
    public void destroy() {
        if (s3Client != null) {
            s3Client.close();
        }
    }

    /**
     * 上传文件到 S3 兼容存储，流式传输不在内存中缓存完整文件。
     *
     * @param objectKey   对象 key
     * @param inputStream 文件输入流
     * @param contentType MIME 类型，可为空
     * @param size        文件大小（字节）
     * @return 上传后的文件访问 URL
     */
    @Override
    public String upload(String objectKey, InputStream inputStream, String contentType, long size) {
        try {
            PutObjectRequest.Builder requestBuilder = PutObjectRequest.builder()
                    .bucket(properties.getBucket())
                    .key(objectKey);
            if (contentType != null && !contentType.isBlank()) {
                requestBuilder.contentType(contentType);
            }

            RequestBody requestBody = size > 0
                    ? RequestBody.fromInputStream(inputStream, size)
                    : RequestBody.fromInputStream(inputStream, inputStream.available());

            s3Client.putObject(requestBuilder.build(), requestBody);

            String externalEndpoint = properties.getExternalEndpoint().replaceAll("/+$", "");
            String fileUrl = externalEndpoint + "/" + properties.getBucket() + "/" + objectKey;
            log.info("文件上传成功，objectKey={}, fileUrl={}", objectKey, fileUrl);
            return fileUrl;
        } catch (Exception e) {
            log.error("文件上传失败，objectKey={}", objectKey, e);
            throw new ServiceException("文件上传到对象存储失败：" + e.getMessage(), BaseErrorCode.REMOTE_ERROR);
        }
    }

    /**
     * 从 S3 兼容存储下载文件，返回输入流由调用方负责关闭。
     *
     * @param objectKey 对象 key
     * @return 文件输入流
     */
    @Override
    public InputStream download(String objectKey) {
        try {
            log.info("开始下载文件，objectKey={}", objectKey);
            return s3Client.getObject(GetObjectRequest.builder()
                    .bucket(properties.getBucket())
                    .key(objectKey)
                    .build());
        } catch (Exception e) {
            log.error("文件下载失败，objectKey={}", objectKey, e);
            throw new ServiceException("文件下载失败：" + e.getMessage(), BaseErrorCode.REMOTE_ERROR);
        }
    }

    /**
     * 删除 S3 兼容存储中的文件。
     *
     * @param objectKey 对象 key
     */
    @Override
    public void delete(String objectKey) {
        try {
            s3Client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(properties.getBucket())
                    .key(objectKey)
                    .build());
            log.info("文件删除成功，objectKey={}", objectKey);
        } catch (Exception e) {
            log.error("文件删除失败，objectKey={}", objectKey, e);
            throw new ServiceException("文件删除失败：" + e.getMessage(), BaseErrorCode.REMOTE_ERROR);
        }
    }
}
