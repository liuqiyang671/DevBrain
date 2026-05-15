package edu.cqupt.devbrain.commerce.evaluation.service.impl;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import edu.cqupt.devbrain.commerce.evaluation.dao.entity.EvaluationCaseDO;
import edu.cqupt.devbrain.commerce.evaluation.dao.mapper.EvaluationCaseMapper;
import edu.cqupt.devbrain.commerce.evaluation.dao.mapper.EvaluationDatasetMapper;
import edu.cqupt.devbrain.commerce.evaluation.dto.req.EvaluationCaseReq;
import edu.cqupt.devbrain.commerce.evaluation.dto.resp.EvaluationCaseResp;
import edu.cqupt.devbrain.commerce.evaluation.service.EvaluationCaseService;
import edu.cqupt.devbrain.commerce.evaluation.support.EvaluationJsonSupport;
import edu.cqupt.devbrain.framework.context.UserContext;
import edu.cqupt.devbrain.framework.exception.ClientException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 评测用例服务实现类。
 * 提供评测用例的创建、更新、分页查询和删除功能。
 */
@Service
@RequiredArgsConstructor
public class EvaluationCaseServiceImpl implements EvaluationCaseService {

    private final EvaluationCaseMapper caseMapper;
    private final EvaluationDatasetMapper datasetMapper;

    @Override
    @Transactional
    public EvaluationCaseResp create(String datasetId, EvaluationCaseReq request) {
        requireDataset(datasetId);
        EvaluationCaseDO entity = new EvaluationCaseDO();
        entity.setId(IdUtil.getSnowflakeNextIdStr());
        entity.setDatasetId(datasetId);
        fill(entity, request);
        String userId = UserContext.requireUser().userId();
        entity.setCreatedBy(userId);
        entity.setUpdatedBy(userId);
        caseMapper.insert(entity);
        return toResp(entity);
    }

    @Override
    @Transactional
    public EvaluationCaseResp update(String caseId, EvaluationCaseReq request) {
        EvaluationCaseDO entity = requireCase(caseId);
        fill(entity, request);
        entity.setUpdatedBy(UserContext.requireUser().userId());
        caseMapper.updateById(entity);
        return toResp(entity);
    }

    @Override
    public IPage<EvaluationCaseResp> page(String datasetId, long pageNo, long pageSize) {
        IPage<EvaluationCaseDO> page = caseMapper.selectPage(new Page<>(Math.max(1, pageNo), Math.min(Math.max(1, pageSize), 100)),
                Wrappers.lambdaQuery(EvaluationCaseDO.class)
                        .eq(EvaluationCaseDO::getDatasetId, datasetId)
                        .eq(EvaluationCaseDO::getDeleted, 0)
                        .orderByAsc(EvaluationCaseDO::getCaseNo));
        return page.convert(this::toResp);
    }

    @Override
    @Transactional
    public void delete(String caseId) {
        caseMapper.deleteById(requireCase(caseId));
    }

    private void fill(EvaluationCaseDO entity, EvaluationCaseReq request) {
        entity.setCaseNo(StringUtils.hasText(request.caseNo()) ? request.caseNo() : IdUtil.fastSimpleUUID().substring(0, 8));
        entity.setScenario(clean(request.scenario()));
        entity.setQuestion(required(request.question(), "问题不能为空"));
        entity.setTurnsJson(EvaluationJsonSupport.write(request.turns()));
        entity.setContextJson(EvaluationJsonSupport.write(request.context()));
        entity.setExpectedAnswer(clean(request.expectedAnswer()));
        entity.setExpectedIntent(clean(request.expectedIntent()));
        entity.setExpectedSlots(EvaluationJsonSupport.write(request.expectedSlots()));
        entity.setExpectedProductIds(EvaluationJsonSupport.write(request.expectedProductIds()));
        entity.setExpectedChunkIds(EvaluationJsonSupport.write(request.expectedChunkIds()));
        entity.setMustHitKeywords(EvaluationJsonSupport.write(request.mustHitKeywords()));
        entity.setForbiddenClaims(EvaluationJsonSupport.write(request.forbiddenClaims()));
        entity.setTags(EvaluationJsonSupport.write(request.tags()));
    }

    private void requireDataset(String datasetId) {
        if (datasetMapper.selectById(datasetId) == null) {
            throw new ClientException("评测集不存在");
        }
    }

    private EvaluationCaseDO requireCase(String caseId) {
        EvaluationCaseDO entity = caseMapper.selectById(caseId);
        if (entity == null || Integer.valueOf(1).equals(entity.getDeleted())) {
            throw new ClientException("评测用例不存在或已删除");
        }
        return entity;
    }

    private EvaluationCaseResp toResp(EvaluationCaseDO entity) {
        return new EvaluationCaseResp(entity.getId(), entity.getDatasetId(), entity.getCaseNo(), entity.getScenario(),
                entity.getQuestion(), entity.getExpectedIntent(), entity.getExpectedProductIds(),
                entity.getMustHitKeywords(), entity.getForbiddenClaims(), entity.getCreateTime(), entity.getUpdateTime());
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
