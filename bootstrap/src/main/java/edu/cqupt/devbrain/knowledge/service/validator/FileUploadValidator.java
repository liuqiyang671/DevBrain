package edu.cqupt.devbrain.knowledge.service.validator;

import edu.cqupt.devbrain.framework.exception.ClientException;
import edu.cqupt.devbrain.knowledge.config.UploadProperties;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.net.URLConnection;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * 文件上传校验器 —— 负责文件基础校验、文件名清洗和文件类型白名单校验。
 * <p>
 * 由 Service 层调用，Controller 不直接使用。
 */
@Component
@RequiredArgsConstructor
public class FileUploadValidator {

    private final UploadProperties uploadProperties;
    private Set<String> allowedExtensionSet;
    private Set<String> blockedExtensionSet;

    @PostConstruct
    public void init() {
        this.allowedExtensionSet = new HashSet<>(uploadProperties.getAllowedExtensions());
        this.blockedExtensionSet = new HashSet<>(uploadProperties.getBlockedExtensions());
    }

    /**
     * 校验上传文件的基础合法性（非空、文件名非空、大小 > 0、大小不超过配置上限）。
     *
     * @param file 上传的文件
     */
    public void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ClientException("上传文件不能为空");
        }
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || originalFilename.isBlank()) {
            throw new ClientException("文件名不能为空");
        }
        if (file.getSize() <= 0) {
            throw new ClientException("文件大小必须大于 0");
        }
        long maxSizeBytes = uploadProperties.getMaxFileSize().toBytes();
        if (file.getSize() > maxSizeBytes) {
            throw new ClientException("文件大小超过限制，最大允许 "
                    + uploadProperties.getMaxFileSize().toMegabytes() + "MB");
        }
    }

    /**
     * 校验文件类型：黑名单优先 → 白名单 → MIME 危险类型检查。
     *
     * @param file      上传的文件
     * @param extension 已提取的小写扩展名
     */
    public void validateFileType(MultipartFile file, String extension) {
        if (!extension.isEmpty() && blockedExtensionSet.contains(extension)) {
            throw new ClientException("不支持上传 " + extension + " 类型文件");
        }
        if (extension.isEmpty() || !allowedExtensionSet.contains(extension)) {
            throw new ClientException("不支持上传 " + (extension.isEmpty() ? "未知" : extension) + " 类型文件，"
                    + "允许的类型：" + String.join(", ", uploadProperties.getAllowedExtensions()));
        }
        String guessedMime = URLConnection.guessContentTypeFromName(file.getOriginalFilename());
        if (guessedMime != null && isDangerousMime(guessedMime)) {
            throw new ClientException("不支持上传该类型的文件（MIME: " + guessedMime + "）");
        }
    }

    /**
     * 清洗原始文件名，去除路径分隔符和路径穿越字符，仅保留基础文件名。
     */
    public String sanitizeFilename(String originalFilename) {
        if (originalFilename == null || originalFilename.isBlank()) {
            throw new ClientException("文件名不能为空");
        }
        String sanitized = originalFilename;
        int lastSlash = Math.max(sanitized.lastIndexOf('/'), sanitized.lastIndexOf('\\'));
        if (lastSlash >= 0) {
            sanitized = sanitized.substring(lastSlash + 1);
        }
        sanitized = sanitized.replace("\0", "");
        if (sanitized.isBlank()) {
            throw new ClientException("文件名清洗后为空");
        }
        return sanitized;
    }

    /**
     * 提取文件扩展名（不含点号），统一转小写。无扩展名返回空字符串。
     */
    public String extractExtension(String filename) {
        if (filename == null) {
            return "";
        }
        int dotIndex = filename.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == filename.length() - 1) {
            return "";
        }
        return filename.substring(dotIndex + 1).toLowerCase(Locale.ROOT);
    }

    private boolean isDangerousMime(String mime) {
        return mime.startsWith("application/x-executable")
                || mime.startsWith("application/x-msdos-program")
                || mime.startsWith("application/x-sh")
                || mime.startsWith("application/x-bat")
                || mime.equals("application/x-java-archive")
                || mime.equals("application/java-archive")
                || mime.equals("application/x-sharedlib")
                || mime.startsWith("application/x-msdownload");
    }
}
