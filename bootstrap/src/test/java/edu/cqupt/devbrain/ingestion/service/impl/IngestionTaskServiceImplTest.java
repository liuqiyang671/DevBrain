package edu.cqupt.devbrain.ingestion.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import edu.cqupt.devbrain.core.chunk.VectorChunk;
import edu.cqupt.devbrain.framework.context.LoginUser;
import edu.cqupt.devbrain.framework.context.UserContext;
import edu.cqupt.devbrain.ingestion.controller.request.ExecuteTaskRequest;
import edu.cqupt.devbrain.ingestion.controller.request.IngestionTaskPageRequest;
import edu.cqupt.devbrain.ingestion.controller.vo.IngestionTaskNodeVO;
import edu.cqupt.devbrain.ingestion.controller.vo.IngestionTaskVO;
import edu.cqupt.devbrain.ingestion.dao.entity.IngestionTaskDO;
import edu.cqupt.devbrain.ingestion.dao.entity.IngestionTaskNodeDO;
import edu.cqupt.devbrain.ingestion.dao.mapper.IngestionTaskMapper;
import edu.cqupt.devbrain.ingestion.dao.mapper.IngestionTaskNodeMapper;
import edu.cqupt.devbrain.ingestion.domain.IngestionStatus;
import edu.cqupt.devbrain.ingestion.domain.SourceType;
import edu.cqupt.devbrain.ingestion.domain.context.IngestionContext;
import edu.cqupt.devbrain.ingestion.domain.context.NodeLog;
import edu.cqupt.devbrain.ingestion.domain.pipeline.NodeConfig;
import edu.cqupt.devbrain.ingestion.domain.pipeline.PipelineDefinition;
import edu.cqupt.devbrain.ingestion.domain.result.IngestionResult;
import edu.cqupt.devbrain.ingestion.engine.IngestionEngine;
import edu.cqupt.devbrain.ingestion.service.IngestionPipelineService;
import edu.cqupt.devbrain.knowledge.service.validator.FileUploadValidator;
import edu.cqupt.devbrain.knowledge.storage.FileStorageService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * IngestionTaskServiceImpl 单元测试，覆盖任务执行、上传委托和任务日志查询。
 */
class IngestionTaskServiceImplTest {

    private final IngestionPipelineService pipelineService = mock(IngestionPipelineService.class);
    private final IngestionEngine ingestionEngine = mock(IngestionEngine.class);
    private final IngestionTaskMapper taskMapper = mock(IngestionTaskMapper.class);
    private final IngestionTaskNodeMapper taskNodeMapper = mock(IngestionTaskNodeMapper.class);
    private final FileStorageService fileStorageService = mock(FileStorageService.class);
    private final FileUploadValidator fileUploadValidator = mock(FileUploadValidator.class);
    private final IngestionTaskServiceImpl taskService = new IngestionTaskServiceImpl(
            pipelineService,
            ingestionEngine,
            taskMapper,
            taskNodeMapper,
            fileStorageService,
            fileUploadValidator
    );

    @AfterEach
    void clearUserContext() {
        UserContext.clear();
    }

    /**
     * execute 应创建 RUNNING 任务，调用引擎执行，并持久化最终任务状态和节点日志。
     */
    @Test
    void executeShouldPersistTaskAndNodeLogs() {
        UserContext.set(loginUser());
        when(pipelineService.getDefinition("pipe-1")).thenReturn(pipeline());
        assignTaskId("task-1");
        completeEngineWithOneNodeLog();

        IngestionResult result = taskService.execute(new ExecuteTaskRequest(
                "pipe-1",
                "URL",
                "https://example.com/doc.md",
                "doc.md",
                Map.of("kb_id", "kb-1")
        ));

        ArgumentCaptor<IngestionTaskDO> insertCaptor = ArgumentCaptor.forClass(IngestionTaskDO.class);
        verify(taskMapper).insert(insertCaptor.capture());
        IngestionTaskDO inserted = insertCaptor.getValue();
        assertEquals("pipe-1", inserted.getPipelineId());
        assertEquals("URL", inserted.getSourceType());
        assertEquals("https://example.com/doc.md", inserted.getSourceLocation());
        assertEquals("RUNNING", inserted.getStatus());
        assertEquals("user-1", inserted.getCreatedBy());

        ArgumentCaptor<IngestionTaskDO> updateCaptor = ArgumentCaptor.forClass(IngestionTaskDO.class);
        verify(taskMapper).updateById(updateCaptor.capture());
        IngestionTaskDO updated = updateCaptor.getValue();
        assertEquals("COMPLETED", updated.getStatus());
        assertEquals(1, updated.getChunkCount());
        assertTrue(updated.getLogsJson().contains("fetch ok"));
        assertTrue(updated.getMetadataJson().contains("kb-1"));

        ArgumentCaptor<IngestionTaskNodeDO> nodeCaptor = ArgumentCaptor.forClass(IngestionTaskNodeDO.class);
        verify(taskNodeMapper).insert(nodeCaptor.capture());
        assertEquals("fetch-1", nodeCaptor.getValue().getNodeId());
        assertEquals("COMPLETED", nodeCaptor.getValue().getStatus());
        assertEquals(12L, nodeCaptor.getValue().getDurationMs());
        assertEquals(IngestionStatus.COMPLETED, result.getStatus());
    }

    /**
     * upload 应先保存文件到 FileStorageService，再以 FILE 来源执行 Pipeline。
     */
    @Test
    void uploadShouldStoreFileAndExecutePipelineWithFileSource() {
        UserContext.set(loginUser());
        when(pipelineService.getDefinition("pipe-1")).thenReturn(pipeline());
        when(fileStorageService.upload(anyString(), any(), eq("text/markdown"), eq(11L))).thenReturn("http://files/doc.md");
        assignTaskId("task-2");
        completeEngineWithOneNodeLog();
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "doc.md",
                "text/markdown",
                "hello world".getBytes(StandardCharsets.UTF_8)
        );

        IngestionResult result = taskService.upload("pipe-1", file);

        ArgumentCaptor<String> objectKeyCaptor = ArgumentCaptor.forClass(String.class);
        verify(fileStorageService).upload(objectKeyCaptor.capture(), any(), eq("text/markdown"), eq(11L));
        assertTrue(objectKeyCaptor.getValue().startsWith("ingestion/"));
        ArgumentCaptor<PipelineDefinition> pipelineCaptor = ArgumentCaptor.forClass(PipelineDefinition.class);
        ArgumentCaptor<IngestionContext> contextCaptor = ArgumentCaptor.forClass(IngestionContext.class);
        verify(ingestionEngine).execute(pipelineCaptor.capture(), contextCaptor.capture());
        assertEquals(SourceType.FILE, contextCaptor.getValue().getSource().getType());
        assertEquals("doc.md", contextCaptor.getValue().getSource().getFileName());
        assertEquals(objectKeyCaptor.getValue(), contextCaptor.getValue().getSource().getLocation());
        assertEquals(IngestionStatus.COMPLETED, result.getStatus());
    }

    /**
     * getTask 和 getTaskNodes 应把任务记录和节点记录转换为前端 VO。
     */
    @Test
    void getTaskAndNodesShouldReturnPersistedRecords() {
        when(taskMapper.selectById("task-1")).thenReturn(taskDO("task-1", "COMPLETED"));
        when(taskNodeMapper.selectList(any())).thenReturn(List.of(taskNodeDO()));

        IngestionTaskVO task = taskService.getTask("task-1");
        List<IngestionTaskNodeVO> nodes = taskService.getTaskNodes("task-1");

        assertEquals("task-1", task.id());
        assertEquals("COMPLETED", task.status());
        assertEquals("kb-1", task.metadata().get("kb_id"));
        assertEquals(1, nodes.size());
        assertEquals("fetch-1", nodes.get(0).nodeId());
        assertEquals("fetch ok", nodes.get(0).output().get("message"));
    }

    /**
     * page 应裁剪分页边界，并按任务记录转换 VO。
     */
    @Test
    void pageShouldClampAndConvertTaskRecords() {
        when(taskMapper.selectPage(any(), any())).thenAnswer(invocation -> {
            IPage<IngestionTaskDO> page = invocation.getArgument(0);
            page.setRecords(List.of(taskDO("task-1", "COMPLETED")));
            return page;
        });
        IngestionTaskPageRequest request = new IngestionTaskPageRequest();
        request.setPageNo(0);
        request.setPageSize(200);
        request.setPipelineId("pipe-1");

        IPage<IngestionTaskVO> page = taskService.page(request);

        assertEquals(1, page.getCurrent());
        assertEquals(100, page.getSize());
        assertEquals("task-1", page.getRecords().get(0).id());
    }

    private void assignTaskId(String taskId) {
        doAnswer(invocation -> {
            IngestionTaskDO task = invocation.getArgument(0);
            task.setId(taskId);
            return 1;
        }).when(taskMapper).insert(any(IngestionTaskDO.class));
    }

    private void completeEngineWithOneNodeLog() {
        when(ingestionEngine.execute(any(), any())).thenAnswer(invocation -> {
            IngestionContext context = invocation.getArgument(1);
            context.getMetadata().put("kb_id", "kb-1");
            context.getChunks().add(VectorChunk.of("chunk", 0));
            context.getLogs().add(NodeLog.builder()
                    .nodeId("fetch-1")
                    .nodeType("fetcher")
                    .success(true)
                    .message("fetch ok")
                    .durationMs(12L)
                    .build());
            context.setStatus(IngestionStatus.COMPLETED);
            return IngestionResult.builder()
                    .taskId(context.getTaskId())
                    .pipelineId(context.getPipelineId())
                    .status(IngestionStatus.COMPLETED)
                    .chunkCount(1)
                    .message("ok")
                    .build();
        });
    }

    private PipelineDefinition pipeline() {
        return PipelineDefinition.builder()
                .id("pipe-1")
                .name("测试流水线")
                .nodes(List.of(NodeConfig.builder()
                        .nodeId("fetch-1")
                        .nodeType("fetcher")
                        .build()))
                .build();
    }

    private IngestionTaskDO taskDO(String taskId, String status) {
        IngestionTaskDO task = new IngestionTaskDO();
        task.setId(taskId);
        task.setPipelineId("pipe-1");
        task.setSourceType("URL");
        task.setSourceLocation("https://example.com/doc.md");
        task.setStatus(status);
        task.setChunkCount(1);
        task.setMetadataJson("{\"kb_id\":\"kb-1\"}");
        task.setLogsJson("[{\"nodeId\":\"fetch-1\"}]");
        task.setCreatedBy("user-1");
        return task;
    }

    private IngestionTaskNodeDO taskNodeDO() {
        IngestionTaskNodeDO node = new IngestionTaskNodeDO();
        node.setId("task-node-1");
        node.setTaskId("task-1");
        node.setPipelineId("pipe-1");
        node.setNodeId("fetch-1");
        node.setNodeType("fetcher");
        node.setNodeOrder(0);
        node.setStatus("COMPLETED");
        node.setDurationMs(12L);
        node.setOutputJson("{\"message\":\"fetch ok\"}");
        return node;
    }

    private LoginUser loginUser() {
        return new LoginUser("user-1", "alice", "alice@example.com", "Alice", null, Set.of("admin"), Set.of());
    }
}
