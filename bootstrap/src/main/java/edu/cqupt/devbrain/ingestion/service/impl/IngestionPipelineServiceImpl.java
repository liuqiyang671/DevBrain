package edu.cqupt.devbrain.ingestion.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.cqupt.devbrain.framework.context.UserContext;
import edu.cqupt.devbrain.framework.exception.ClientException;
import edu.cqupt.devbrain.framework.exception.ServiceException;
import edu.cqupt.devbrain.framework.errorcode.BaseErrorCode;
import edu.cqupt.devbrain.ingestion.controller.request.CreatePipelineRequest;
import edu.cqupt.devbrain.ingestion.controller.request.IngestionPipelinePageRequest;
import edu.cqupt.devbrain.ingestion.controller.request.NodeConfigRequest;
import edu.cqupt.devbrain.ingestion.controller.request.UpdatePipelineRequest;
import edu.cqupt.devbrain.ingestion.controller.vo.IngestionPipelineNodeVO;
import edu.cqupt.devbrain.ingestion.controller.vo.IngestionPipelineVO;
import edu.cqupt.devbrain.ingestion.dao.entity.IngestionPipelineDO;
import edu.cqupt.devbrain.ingestion.dao.entity.IngestionPipelineNodeDO;
import edu.cqupt.devbrain.ingestion.dao.mapper.IngestionPipelineMapper;
import edu.cqupt.devbrain.ingestion.dao.mapper.IngestionPipelineNodeMapper;
import edu.cqupt.devbrain.ingestion.domain.pipeline.NodeConfig;
import edu.cqupt.devbrain.ingestion.domain.pipeline.PipelineDefinition;
import edu.cqupt.devbrain.ingestion.service.IngestionPipelineService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 摄入流水线定义服务实现，负责持久化前端配置的节点链并组装执行定义。
 */
@Service
@RequiredArgsConstructor
public class IngestionPipelineServiceImpl implements IngestionPipelineService {

    /**
     * JSON 序列化工具，用于 settings_json 和 condition_json 字段。
     */
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final IngestionPipelineMapper pipelineMapper;
    private final IngestionPipelineNodeMapper nodeMapper;

    /**
     * 加载流水线主记录和节点记录，并组装为 PipelineDefinition。
     */
    @Override
    public PipelineDefinition getDefinition(String pipelineId) {
        IngestionPipelineDO pipeline = requirePipeline(pipelineId);
        List<IngestionPipelineNodeDO> nodes = listNodes(pipelineId);
        return PipelineDefinition.builder()
                .id(pipeline.getId())
                .name(pipeline.getName())
                .description(pipeline.getDescription())
                .nodes(nodes.stream().map(this::toNodeConfig).toList())
                .build();
    }

    /**
     * 创建流水线主记录，并批量插入节点记录。
     */
    @Override
    @Transactional
    public IngestionPipelineVO create(CreatePipelineRequest request) {
        String name = cleanRequired(request.name(), "流水线名称不能为空");
        List<NodeConfigRequest> nodes = safeNodes(request.nodes());
        validateNodes(nodes);

        IngestionPipelineDO pipeline = new IngestionPipelineDO();
        pipeline.setName(name);
        pipeline.setDescription(clean(request.description()));
        pipeline.setCreatedBy(UserContext.requireUser().userId());
        pipelineMapper.insert(pipeline);
        saveNodes(pipeline.getId(), nodes);
        return toVOFromRequests(pipeline, nodes);
    }

    /**
     * 更新流水线主记录；请求传入 nodes 时使用先删后插策略替换节点列表。
     */
    @Override
    @Transactional
    public IngestionPipelineVO update(String id, UpdatePipelineRequest request) {
        IngestionPipelineDO pipeline = requirePipeline(id);
        if (request.name() != null) {
            pipeline.setName(cleanRequired(request.name(), "流水线名称不能为空"));
        }
        if (request.description() != null) {
            pipeline.setDescription(clean(request.description()));
        }
        // 实体中没有 updatedBy 字段，这里保留创建人不变，更新时间由 MyMetaObjectHandler 自动填充。
        pipelineMapper.updateById(pipeline);

        if (request.nodes() != null) {
            List<NodeConfigRequest> nodes = safeNodes(request.nodes());
            validateNodes(nodes);
            deleteNodes(id);
            saveNodes(id, nodes);
            return toVOFromRequests(pipeline, nodes);
        }
        return toVO(pipeline, listNodes(id));
    }

    /**
     * 删除流水线及节点。节点先删，避免主记录删除后留下孤儿配置。
     */
    @Override
    @Transactional
    public void delete(String id) {
        requirePipeline(id);
        deleteNodes(id);
        pipelineMapper.deleteById(id);
    }

    /**
     * 分页查询流水线，分页边界在 Service 层兜底裁剪。
     */
    @Override
    public IPage<IngestionPipelineVO> page(IngestionPipelinePageRequest request) {
        long current = Math.max(1, request.getPageNo());
        long size = Math.min(Math.max(1, request.getPageSize()), 100);
        String keyword = clean(request.getKeyword());
        IPage<IngestionPipelineDO> result = pipelineMapper.selectPage(new Page<>(current, size),
                Wrappers.lambdaQuery(IngestionPipelineDO.class)
                        .and(StringUtils.hasText(keyword), wrapper -> wrapper
                                .like(IngestionPipelineDO::getName, keyword)
                                .or()
                                .like(IngestionPipelineDO::getDescription, keyword))
                        .orderByDesc(IngestionPipelineDO::getUpdateTime));
        return result.convert(pipeline -> toVO(pipeline, countNodes(pipeline.getId()), List.of()));
    }

    /**
     * 查询并校验流水线存在。
     */
    private IngestionPipelineDO requirePipeline(String id) {
        if (!StringUtils.hasText(id)) {
            throw new ClientException("流水线 ID 不能为空");
        }
        IngestionPipelineDO pipeline = pipelineMapper.selectById(id);
        if (pipeline == null) {
            throw new ClientException("流水线不存在");
        }
        return pipeline;
    }

    /**
     * 查询流水线节点，并按 sort_order、node_id 稳定排序。
     */
    private List<IngestionPipelineNodeDO> listNodes(String pipelineId) {
        return nodeMapper.selectList(Wrappers.lambdaQuery(IngestionPipelineNodeDO.class)
                .eq(IngestionPipelineNodeDO::getPipelineId, pipelineId)
                .orderByAsc(IngestionPipelineNodeDO::getSortOrder)
                .orderByAsc(IngestionPipelineNodeDO::getNodeId));
    }

    /**
     * 统计流水线节点数量。
     */
    private long countNodes(String pipelineId) {
        Long count = nodeMapper.selectCount(Wrappers.lambdaQuery(IngestionPipelineNodeDO.class)
                .eq(IngestionPipelineNodeDO::getPipelineId, pipelineId));
        return count == null ? 0L : count;
    }

    /**
     * 保存节点列表，sort_order 使用请求顺序。
     */
    private void saveNodes(String pipelineId, List<NodeConfigRequest> nodes) {
        for (int i = 0; i < nodes.size(); i++) {
            nodeMapper.insert(toNodeDO(pipelineId, nodes.get(i), i));
        }
    }

    /**
     * 删除流水线下全部节点。
     */
    private void deleteNodes(String pipelineId) {
        LambdaQueryWrapper<IngestionPipelineNodeDO> wrapper = Wrappers.lambdaQuery(IngestionPipelineNodeDO.class)
                .eq(IngestionPipelineNodeDO::getPipelineId, pipelineId);
        nodeMapper.delete(wrapper);
    }

    /**
     * 校验节点 ID、类型和链路引用。
     */
    private void validateNodes(List<NodeConfigRequest> nodes) {
        Set<String> nodeIds = new HashSet<>();
        for (NodeConfigRequest node : nodes) {
            String nodeId = cleanRequired(node.nodeId(), "nodeId 不能为空");
            cleanRequired(node.nodeType(), "nodeType 不能为空");
            if (!nodeIds.add(nodeId)) {
                throw new ClientException("节点 ID 重复：" + nodeId);
            }
        }
        for (NodeConfigRequest node : nodes) {
            String nextNodeId = clean(node.nextNodeId());
            if (StringUtils.hasText(nextNodeId) && !nodeIds.contains(nextNodeId)) {
                throw new ClientException("nextNodeId 指向不存在的节点：" + nextNodeId);
            }
        }
    }

    /**
     * 将请求节点转换为数据库实体。
     */
    private IngestionPipelineNodeDO toNodeDO(String pipelineId, NodeConfigRequest request, int sortOrder) {
        IngestionPipelineNodeDO node = new IngestionPipelineNodeDO();
        node.setPipelineId(pipelineId);
        node.setNodeId(cleanRequired(request.nodeId(), "nodeId 不能为空"));
        node.setNodeType(cleanRequired(request.nodeType(), "nodeType 不能为空"));
        node.setNextNodeId(clean(request.nextNodeId()));
        node.setSettingsJson(toJson(request.settings() == null ? Map.of() : request.settings()));
        node.setConditionJson(clean(request.condition()));
        node.setSortOrder(sortOrder);
        return node;
    }

    /**
     * 将数据库节点转换为执行节点配置。
     */
    private NodeConfig toNodeConfig(IngestionPipelineNodeDO node) {
        return NodeConfig.builder()
                .nodeId(node.getNodeId())
                .nodeType(node.getNodeType())
                .settings(parseJson(node.getSettingsJson(), true))
                .condition(parseJson(node.getConditionJson(), false))
                .nextNodeId(node.getNextNodeId())
                .build();
    }

    /**
     * 将主记录和节点记录转换为详情 VO。
     */
    private IngestionPipelineVO toVO(IngestionPipelineDO pipeline, List<IngestionPipelineNodeDO> nodes) {
        return toVO(pipeline, nodes == null ? 0 : nodes.size(),
                nodes == null ? List.of() : nodes.stream().map(this::toNodeVO).toList());
    }

    /**
     * 将请求节点直接转换为 VO，用于 create/update 后避免不必要的数据库回查。
     */
    private IngestionPipelineVO toVOFromRequests(IngestionPipelineDO pipeline, List<NodeConfigRequest> nodes) {
        List<NodeConfigRequest> safeNodes = safeNodes(nodes);
        List<IngestionPipelineNodeVO> nodeVOs = new ArrayList<>();
        for (int i = 0; i < safeNodes.size(); i++) {
            nodeVOs.add(toNodeVO(safeNodes.get(i), i));
        }
        return toVO(pipeline, safeNodes.size(), nodeVOs);
    }

    /**
     * 将主记录转换为分页 VO。
     */
    private IngestionPipelineVO toVO(IngestionPipelineDO pipeline, long nodeCount, List<IngestionPipelineNodeVO> nodes) {
        return new IngestionPipelineVO(
                pipeline.getId(),
                pipeline.getName(),
                pipeline.getDescription(),
                Math.toIntExact(nodeCount),
                nodes,
                pipeline.getCreatedBy(),
                pipeline.getCreateTime(),
                pipeline.getUpdateTime()
        );
    }

    /**
     * 将节点实体转换为视图对象。
     */
    private IngestionPipelineNodeVO toNodeVO(IngestionPipelineNodeDO node) {
        return new IngestionPipelineNodeVO(
                node.getNodeId(),
                node.getNodeType(),
                parseSettingsMap(node.getSettingsJson()),
                node.getConditionJson(),
                node.getNextNodeId(),
                node.getSortOrder()
        );
    }

    /**
     * 将请求节点转换为视图对象。
     */
    private IngestionPipelineNodeVO toNodeVO(NodeConfigRequest node, int sortOrder) {
        return new IngestionPipelineNodeVO(
                node.nodeId(),
                node.nodeType(),
                node.settings() == null ? Map.of() : node.settings(),
                node.condition(),
                node.nextNodeId(),
                sortOrder
        );
    }

    /**
     * 将 Map 序列化为 JSON。
     */
    private String toJson(Map<String, Object> settings) {
        try {
            return OBJECT_MAPPER.writeValueAsString(settings);
        } catch (JsonProcessingException ex) {
            throw new ServiceException("节点配置序列化失败", ex, BaseErrorCode.SERVICE_ERROR);
        }
    }

    /**
     * 解析 JsonNode。settings 为空时返回空对象；condition 为空时返回 null。
     */
    private JsonNode parseJson(String json, boolean emptyObjectWhenBlank) {
        if (!StringUtils.hasText(json)) {
            return emptyObjectWhenBlank ? OBJECT_MAPPER.createObjectNode() : null;
        }
        String cleaned = json.trim();
        try {
            if (looksLikeJson(cleaned)) {
                return OBJECT_MAPPER.readTree(cleaned);
            }
            return OBJECT_MAPPER.getNodeFactory().textNode(cleaned);
        } catch (JsonProcessingException ex) {
            return OBJECT_MAPPER.getNodeFactory().textNode(cleaned);
        }
    }

    /**
     * 解析 settings JSON 为 Map，详情接口展示使用。
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> parseSettingsMap(String json) {
        if (!StringUtils.hasText(json)) {
            return Map.of();
        }
        try {
            Object value = OBJECT_MAPPER.readValue(json, Object.class);
            if (value instanceof Map<?, ?> map) {
                return (Map<String, Object>) map;
            }
            return Map.of();
        } catch (JsonProcessingException ex) {
            return Map.of();
        }
    }

    /**
     * 判断文本是否看起来像 JSON 对象、数组或字面量。
     */
    private boolean looksLikeJson(String value) {
        return value.startsWith("{")
                || value.startsWith("[")
                || "true".equalsIgnoreCase(value)
                || "false".equalsIgnoreCase(value)
                || "null".equalsIgnoreCase(value);
    }

    /**
     * 归一化节点列表，避免 null 分支散落在业务逻辑中。
     */
    private List<NodeConfigRequest> safeNodes(List<NodeConfigRequest> nodes) {
        return nodes == null ? new ArrayList<>() : nodes;
    }

    /**
     * 清理字符串并校验必填。
     */
    private String cleanRequired(String value, String message) {
        String cleaned = clean(value);
        if (!StringUtils.hasText(cleaned)) {
            throw new ClientException(message);
        }
        return cleaned;
    }

    /**
     * 去除字符串前后空白，保留 null 语义。
     */
    private String clean(String value) {
        return value == null ? null : value.trim();
    }
}
