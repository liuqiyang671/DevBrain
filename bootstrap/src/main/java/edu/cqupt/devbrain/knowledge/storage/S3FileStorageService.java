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

    @PreDestroy
    public void destroy() {
        if (s3Client != null) {
            s3Client.close();
        }
    }

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
