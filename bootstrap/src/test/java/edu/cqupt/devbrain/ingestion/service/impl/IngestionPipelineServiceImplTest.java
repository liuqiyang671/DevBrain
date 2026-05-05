package edu.cqupt.devbrain.ingestion.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import edu.cqupt.devbrain.framework.context.LoginUser;
import edu.cqupt.devbrain.framework.context.UserContext;
import edu.cqupt.devbrain.framework.exception.ClientException;
import edu.cqupt.devbrain.ingestion.controller.request.CreatePipelineRequest;
import edu.cqupt.devbrain.ingestion.controller.request.IngestionPipelinePageRequest;
import edu.cqupt.devbrain.ingestion.controller.request.NodeConfigRequest;
import edu.cqupt.devbrain.ingestion.controller.request.UpdatePipelineRequest;
import edu.cqupt.devbrain.ingestion.controller.vo.IngestionPipelineVO;
import edu.cqupt.devbrain.ingestion.dao.entity.IngestionPipelineDO;
import edu.cqupt.devbrain.ingestion.dao.entity.IngestionPipelineNodeDO;
import edu.cqupt.devbrain.ingestion.dao.mapper.IngestionPipelineMapper;
import edu.cqupt.devbrain.ingestion.dao.mapper.IngestionPipelineNodeMapper;
import edu.cqupt.devbrain.ingestion.domain.pipeline.PipelineDefinition;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * IngestionPipelineServiceImpl 单元测试，覆盖流水线定义持久化和 PipelineDefinition 组装逻辑。
 */
class IngestionPipelineServiceImplTest {

    private final IngestionPipelineMapper pipelineMapper = mock(IngestionPipelineMapper.class);
    private final IngestionPipelineNodeMapper nodeMapper = mock(IngestionPipelineNodeMapper.class);
    private final IngestionPipelineServiceImpl pipelineService =
            new IngestionPipelineServiceImpl(pipelineMapper, nodeMapper);

    @AfterEach
    void clearUserContext() {
        UserContext.clear();
    }

    /**
     * 创建流水线时应保存主记录和节点记录，并写入当前用户为创建人。
     */
    @Test
    void createShouldPersistPipelineAndNodes() {
        UserContext.set(loginUser());

        IngestionPipelineVO result = pipelineService.create(new CreatePipelineRequest(
                "默认摄入流水线",
                "解析并分块",
                List.of(node("fetch-1", "fetcher", Map.of("source", "file"), null, "parse-1"),
                        node("parse-1", "parser", Map.of(), "true", null))
        ));

        ArgumentCaptor<IngestionPipelineDO> pipelineCaptor = ArgumentCaptor.forClass(IngestionPipelineDO.class);
        verify(pipelineMapper).insert(pipelineCaptor.capture());
        IngestionPipelineDO savedPipeline = pipelineCaptor.getValue();
        assertEquals("默认摄入流水线", savedPipeline.getName());
        assertEquals("解析并分块", savedPipeline.getDescription());
        assertEquals("user-1", savedPipeline.getCreatedBy());

        ArgumentCaptor<IngestionPipelineNodeDO> nodeCaptor = ArgumentCaptor.forClass(IngestionPipelineNodeDO.class);
        verify(nodeMapper, org.mockito.Mockito.times(2)).insert(nodeCaptor.capture());
        List<IngestionPipelineNodeDO> savedNodes = nodeCaptor.getAllValues();
        assertEquals("fetch-1", savedNodes.get(0).getNodeId());
        assertEquals("fetcher", savedNodes.get(0).getNodeType());
        assertEquals("{\"source\":\"file\"}", savedNodes.get(0).getSettingsJson());
        assertEquals("true", savedNodes.get(1).getConditionJson());
        assertEquals(2, result.nodeCount());
    }

    /**
     * getDefinition 应加载主记录和节点记录，并把 JSON 字符串转换回 JsonNode。
     */
    @Test
    void getDefinitionShouldAssemblePipelineDefinition() {
        when(pipelineMapper.selectById("pipe-1")).thenReturn(existingPipeline());
        when(nodeMapper.selectList(any())).thenReturn(List.of(
                nodeDO("fetch-1", "fetcher", "{\"source\":\"file\"}", null, "parse-1", 0),
                nodeDO("parse-1", "parser", "{}", "true", null, 1)
        ));

        PipelineDefinition definition = pipelineService.getDefinition("pipe-1");

        assertEquals("pipe-1", definition.getId());
        assertEquals("默认摄入流水线", definition.getName());
        assertEquals(2, definition.getNodes().size());
        assertEquals("file", definition.getNodes().get(0).getSettings().get("source").asText());
        assertEquals("true", definition.getNodes().get(1).getCondition().asText());
    }

    /**
     * 更新流水线时应更新主记录，并先删除旧节点再插入新节点。
     */
    @Test
    void updateShouldReplaceNodes() {
        UserContext.set(loginUser());
        when(pipelineMapper.selectById("pipe-1")).thenReturn(existingPipeline());

        IngestionPipelineVO result = pipelineService.update("pipe-1", new UpdatePipelineRequest(
                "新版流水线",
                "新增增强节点",
                List.of(node("enhance-1", "enhancer", Map.of("tasks", List.of("KEYWORDS")), null, null))
        ));

        ArgumentCaptor<IngestionPipelineDO> pipelineCaptor = ArgumentCaptor.forClass(IngestionPipelineDO.class);
        verify(pipelineMapper).updateById(pipelineCaptor.capture());
        assertEquals("新版流水线", pipelineCaptor.getValue().getName());
        verify(nodeMapper).delete(any());
        verify(nodeMapper).insert(any(IngestionPipelineNodeDO.class));
        assertEquals(1, result.nodeCount());
    }

    /**
     * 删除流水线应删除节点后再删除主记录，避免残留孤儿节点。
     */
    @Test
    void deleteShouldDeleteNodesAndPipeline() {
        when(pipelineMapper.selectById("pipe-1")).thenReturn(existingPipeline());

        pipelineService.delete("pipe-1");

        verify(nodeMapper).delete(any());
        verify(pipelineMapper).deleteById("pipe-1");
    }

    /**
     * 分页查询应裁剪分页边界，并把节点数量填充到 VO。
     */
    @Test
    void pageShouldClampPageSizeAndFillNodeCount() {
        when(pipelineMapper.selectPage(any(), any())).thenAnswer(invocation -> {
            IPage<IngestionPipelineDO> page = invocation.getArgument(0);
            page.setRecords(List.of(existingPipeline()));
            return page;
        });
        when(nodeMapper.selectCount(any())).thenReturn(3L);

        IPage<IngestionPipelineVO> page = pipelineService.page(pageRequest(0, 200, "默认"));

        assertEquals(1, page.getCurrent());
        assertEquals(100, page.getSize());
        assertEquals(3, page.getRecords().get(0).nodeCount());
    }

    /**
     * 查询不存在的流水线时应抛出业务异常。
     */
    @Test
    void getDefinitionShouldRejectMissingPipeline() {
        when(pipelineMapper.selectById("missing")).thenReturn(null);

        assertThrows(ClientException.class, () -> pipelineService.getDefinition("missing"));

        verify(nodeMapper, never()).selectList(any());
    }

    private NodeConfigRequest node(String nodeId, String nodeType, Map<String, Object> settings,
                                   String condition, String nextNodeId) {
        return new NodeConfigRequest(nodeId, nodeType, settings, condition, nextNodeId);
    }

    private IngestionPipelineDO existingPipeline() {
        IngestionPipelineDO pipeline = new IngestionPipelineDO();
        pipeline.setId("pipe-1");
        pipeline.setName("默认摄入流水线");
        pipeline.setDescription("解析并分块");
        pipeline.setCreatedBy("user-1");
        return pipeline;
    }

    private IngestionPipelineNodeDO nodeDO(String nodeId, String nodeType, String settingsJson,
                                           String conditionJson, String nextNodeId, int sortOrder) {
        IngestionPipelineNodeDO node = new IngestionPipelineNodeDO();
        node.setId("node-" + sortOrder);
        node.setPipelineId("pipe-1");
        node.setNodeId(nodeId);
        node.setNodeType(nodeType);
        node.setSettingsJson(settingsJson);
        node.setConditionJson(conditionJson);
        node.setNextNodeId(nextNodeId);
        node.setSortOrder(sortOrder);
        return node;
    }

    private IngestionPipelinePageRequest pageRequest(long pageNo, long pageSize, String keyword) {
        IngestionPipelinePageRequest request = new IngestionPipelinePageRequest();
        request.setPageNo(pageNo);
        request.setPageSize(pageSize);
        request.setKeyword(keyword);
        return request;
    }

    private LoginUser loginUser() {
        return new LoginUser("user-1", "alice", "alice@example.com", "Alice", null, Set.of("admin"), Set.of());
    }
}
