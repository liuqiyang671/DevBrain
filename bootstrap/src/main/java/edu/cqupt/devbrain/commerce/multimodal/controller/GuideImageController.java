package edu.cqupt.devbrain.commerce.multimodal.controller;

import edu.cqupt.devbrain.commerce.multimodal.dto.GuideImageAnalyzeResp;
import edu.cqupt.devbrain.commerce.multimodal.dto.GuideImageUploadResp;
import edu.cqupt.devbrain.commerce.multimodal.dto.ImageUnderstandingResult;
import edu.cqupt.devbrain.commerce.multimodal.service.GuideImageService;
import edu.cqupt.devbrain.commerce.multimodal.service.ImageUnderstandingService;
import edu.cqupt.devbrain.framework.context.UserContext;
import edu.cqupt.devbrain.framework.convention.Result;
import edu.cqupt.devbrain.framework.web.Results;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

/**
 * 导购图片控制器。
 * 提供图片的上传、查询、内容获取和AI分析API。
 */
@RestController
@RequiredArgsConstructor
public class GuideImageController {

    private final GuideImageService guideImageService;
    private final ImageUnderstandingService imageUnderstandingService;

    /** 上传图片 */
    @PostMapping(value = "/commerce/guide/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<GuideImageUploadResp> upload(@RequestPart("file") MultipartFile file,
                                               @RequestParam(required = false) String sessionId) {
        return Results.success(guideImageService.upload(file, sessionId, UserContext.requireUser().userId()));
    }

    /** 查询图片信息 */
    @GetMapping("/commerce/guide/images/{imageId}")
    public Result<GuideImageUploadResp> get(@PathVariable String imageId) {
        return Results.success(guideImageService.get(imageId, UserContext.requireUser().userId()));
    }

    /** 获取图片文件内容（带缓存） */
    @GetMapping("/commerce/guide/images/{imageId}/content")
    public ResponseEntity<byte[]> content(@PathVariable String imageId) throws Exception {
        String userId = UserContext.requireUser().userId();
        GuideImageUploadResp metadata = guideImageService.get(imageId, userId);
        try (InputStream inputStream = guideImageService.download(imageId, userId)) {
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(metadata.contentType()))
                    .cacheControl(CacheControl.maxAge(10, TimeUnit.MINUTES).cachePrivate())
                    .body(inputStream.readAllBytes());
        }
    }

    /** 对图片执行AI分析 */
    @PostMapping("/commerce/guide/images/{imageId}/analyze")
    public Result<GuideImageAnalyzeResp> analyze(@PathVariable String imageId) {
        guideImageService.getOwnedImage(imageId, UserContext.requireUser().userId());
        ImageUnderstandingResult result = imageUnderstandingService.analyze(imageId);
        return Results.success(new GuideImageAnalyzeResp(imageId, result, Instant.now()));
    }
}
