package edu.cqupt.devbrain.commerce.evaluation.service.impl;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import edu.cqupt.devbrain.commerce.evaluation.dao.entity.EvaluationDatasetDO;
import edu.cqupt.devbrain.commerce.evaluation.dao.mapper.EvaluationDatasetMapper;
import edu.cqupt.devbrain.commerce.evaluation.dto.req.EvaluationDatasetReq;
import edu.cqupt.devbrain.commerce.evaluation.dto.resp.EvaluationDatasetResp;
import edu.cqupt.devbrain.commerce.evaluation.service.EvaluationDatasetService;
import edu.cqupt.devbrain.framework.context.UserContext;
import edu.cqupt.devbrain.framework.exception.ClientException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 评测数据集服务实现类。
 * 提供评测数据集的创建、更新、查询和删除功能。
 */
@Service
@RequiredArgsConstructor
public class EvaluationDatasetServiceImpl implements EvaluationDatasetService {

    private final EvaluationDatasetMapper datasetMapper;

    @Override
    @Transactional
    public EvaluationDatasetResp create(EvaluationDatasetReq request) {
        String userId = UserContext.requireUser().userId();
        EvaluationDatasetDO dataset = new EvaluationDatasetDO();
        dataset.setId(IdUtil.getSnowflakeNextIdStr());
        dataset.setName(required(request.name(), "评测集名称不能为空"));
        dataset.setDescription(clean(request.description()));
        dataset.setStatus(StringUtils.hasText(request.status()) ? request.status() : "enabled");
        dataset.setCreatedBy(userId);
        dataset.setUpdatedBy(userId);
        datasetMapper.insert(dataset);
        return toResp(dataset);
    }

    @Override
    @Transactional
    public EvaluationDatasetResp update(String datasetId, EvaluationDatasetReq request) {
        EvaluationDatasetDO dataset = requireDataset(datasetId);
        dataset.setName(required(request.name(), "评测集名称不能为空"));
        dataset.setDescription(clean(request.description()));
        if (StringUtils.hasText(request.status())) {
            dataset.setStatus(request.status());
        }
        dataset.setUpdatedBy(UserContext.requireUser().userId());
        datasetMapper.updateById(dataset);
        return toResp(dataset);
    }

    @Override
    public EvaluationDatasetResp get(String datasetId) {
        return toResp(requireDataset(datasetId));
    }

    @Override
    public IPage<EvaluationDatasetResp> page(long pageNo, long pageSize, String keyword) {
        IPage<EvaluationDatasetDO> page = datasetMapper.selectPage(new Page<>(Math.max(1, pageNo), Math.min(Math.max(1, pageSize), 100)),
                Wrappers.lambdaQuery(EvaluationDatasetDO.class)
                        .eq(EvaluationDatasetDO::getDeleted, 0)
                        .and(StringUtils.hasText(keyword), wrapper -> wrapper
                                .like(EvaluationDatasetDO::getName, keyword)
                                .or()
                                .like(EvaluationDatasetDO::getDescription, keyword))
                        .orderByDesc(EvaluationDatasetDO::getUpdateTime));
        return page.convert(this::toResp);
    }

    @Override
    @Transactional
    public void delete(String datasetId) {
        datasetMapper.deleteById(requireDataset(datasetId));
    }

    private EvaluationDatasetDO requireDataset(String datasetId) {
        if (!StringUtils.hasText(datasetId)) {
            throw new ClientException("评测集 ID 不能为空");
        }
        EvaluationDatasetDO dataset = datasetMapper.selectById(datasetId);
        if (dataset == null || Integer.valueOf(1).equals(dataset.getDeleted())) {
            throw new ClientException("评测集不存在或已删除");
        }
        return dataset;
    }

    private EvaluationDatasetResp toResp(EvaluationDatasetDO dataset) {
        return new EvaluationDatasetResp(dataset.getId(), dataset.getName(), dataset.getDescription(),
                dataset.getStatus(), dataset.getCreateTime(), dataset.getUpdateTime());
    }

    private String required(String value, String message) {
        String cleaned = clean(value);
        if (!StringUtils.hasText(cleaned)) {
            throw new ClientException(message);
        }
        return cleaned;
    }

    private String clean(String value) {
        return value == null ? null : value.trim();
    }
}
