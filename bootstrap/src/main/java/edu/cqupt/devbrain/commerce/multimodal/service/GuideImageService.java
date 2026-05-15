package edu.cqupt.devbrain.commerce.multimodal.service;

import edu.cqupt.devbrain.commerce.multimodal.dao.entity.GuideImageDO;
import edu.cqupt.devbrain.commerce.multimodal.dto.GuideImageUploadResp;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;

/**
 * 导购图片服务接口。
 * 提供图片的上传、查询、下载等基础能力。
 */
public interface GuideImageService {

    /** 上传图片并创建图片记录 */
    GuideImageUploadResp upload(MultipartFile file, String sessionId, String userId);

    /** 查询图片信息 */
    GuideImageUploadResp get(String imageId, String userId);

    /** 获取用户拥有的图片实体（用于内部校验） */
    GuideImageDO getOwnedImage(String imageId, String userId);

    /** 下载图片文件流 */
    InputStream download(String imageId, String userId);
}
