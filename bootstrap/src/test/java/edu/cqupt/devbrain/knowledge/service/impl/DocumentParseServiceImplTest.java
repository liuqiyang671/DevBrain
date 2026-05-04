package edu.cqupt.devbrain.knowledge.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.cqupt.devbrain.auth.core.DigestSupport;
import edu.cqupt.devbrain.core.chunk.ChunkingMode;
import edu.cqupt.devbrain.core.chunk.ChunkingStrategy;
import edu.cqupt.devbrain.core.chunk.ChunkingStrategyFactory;
import edu.cqupt.devbrain.core.chunk.VectorChunk;
import edu.cqupt.devbrain.core.parser.DocumentParser;
import edu.cqupt.devbrain.core.parser.DocumentParserSelector;
import edu.cqupt.devbrain.knowledge.dao.entity.KnowledgeChunkDO;
import edu.cqupt.devbrain.knowledge.dao.entity.KnowledgeDocumentChunkLogDO;
import edu.cqupt.devbrain.knowledge.dao.entity.KnowledgeDocumentDO;
import edu.cqupt.devbrain.knowledge.dao.mapper.KnowledgeChunkMapper;
import edu.cqupt.devbrain.knowledge.dao.mapper.KnowledgeDocumentChunkLogMapper;
import edu.cqupt.devbrain.knowledge.dao.mapper.KnowledgeDocumentMapper;
import edu.cqupt.devbrain.knowledge.storage.FileStorageService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;

class DocumentParseServiceImplTest {

    private final KnowledgeDocumentMapper knowledgeDocumentMapper = mock(KnowledgeDocumentMapper.class);
    private final KnowledgeDocumentChunkLogMapper chunkLogMapper = mock(KnowledgeDocumentChunkLogMapper.class);
    private final KnowledgeChunkMapper knowledgeChunkMapper = mock(KnowledgeChunkMapper.class);
    private final FileStorageService fileStorageService = mock(FileStorageService.class);
    private final DocumentParserSelector parserSelector = mock(DocumentParserSelector.class);
    private final ChunkingStrategyFactory chunkingStrategyFactory = mock(ChunkingStrategyFactory.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final DocumentParsePersistenceService persistenceService = mock(DocumentParsePersistenceService.class);

    private final DocumentParseServiceImpl service = new DocumentParseServiceImpl(
            knowledgeDocumentMapper, chunkLogMapper, knowledgeChunkMapper, fileStorageService,
            parserSelector, chunkingStrategyFactory, objectMapper, persistenceService
    );

    @Test
    void parseAndChunkCreatesRunningLogWithKnowledgeBaseId() {
        KnowledgeDocumentDO document = document();
        when(knowledgeDocumentMapper.selectById("doc-1")).thenReturn(document);
        when(chunkLogMapper.selectLatestByDocId("doc-1")).thenReturn(null);
        when(fileStorageService.download("doc-1.md"))
                .thenReturn(new ByteArrayInputStream("hello devbrain".getBytes()));

        DocumentParser parser = mock(DocumentParser.class);
        when(parser.extractText(any(InputStream.class), eq("研发手册.md"))).thenReturn("hello devbrain");
        when(parserSelector.selectByMimeType("text/markdown")).thenReturn(parser);

        ChunkingStrategy strategy = mock(ChunkingStrategy.class);
        when(strategy.chunk(anyString(), any())).thenReturn(List.of(new VectorChunk("chunk-1", 0, "hello devbrain")));
        when(chunkingStrategyFactory.requireStrategy(ChunkingMode.FIXED_SIZE)).thenReturn(strategy);

        service.parseAndChunk("doc-1");

        ArgumentCaptor<KnowledgeDocumentChunkLogDO> logCaptor =
                ArgumentCaptor.forClass(KnowledgeDocumentChunkLogDO.class);
        verify(chunkLogMapper).insert(logCaptor.capture());
        KnowledgeDocumentChunkLogDO logRecord = logCaptor.getValue();
        assertEquals("kb-1", logRecord.getKbId());
        assertEquals("doc-1", logRecord.getDocId());
        assertNotNull(logRecord.getStartTime());
    }

    @Test
    void persistenceWritesRequiredChunkFieldsFromDocument() {
        DocumentParsePersistenceService persistence = new DocumentParsePersistenceService(
                knowledgeDocumentMapper, chunkLogMapper, knowledgeChunkMapper, objectMapper
        );
        KnowledgeDocumentDO document = document();
        KnowledgeDocumentChunkLogDO logRecord = new KnowledgeDocumentChunkLogDO();
        logRecord.setId("log-1");
        logRecord.setDocId("doc-1");
        logRecord.setKbId("kb-1");

        persistence.persistChunksAndSuccess(document, logRecord,
                List.of(new VectorChunk("chunk-1", 0, "hello devbrain")),
                10L, 20L, System.currentTimeMillis(), System.currentTimeMillis());

        ArgumentCaptor<List<KnowledgeChunkDO>> chunksCaptor = ArgumentCaptor.forClass(List.class);
        verify(knowledgeChunkMapper).insertBatch(chunksCaptor.capture());
        KnowledgeChunkDO chunk = chunksCaptor.getValue().get(0);
        assertEquals("kb-1", chunk.getKbId());
        assertEquals("doc-1", chunk.getDocId());
        assertEquals("hello devbrain", chunk.getContent());
        assertEquals(DigestSupport.sha256("hello devbrain"), chunk.getContentHash());
        assertEquals(14, chunk.getCharCount());
        assertEquals(1, chunk.getEnabled());
        assertEquals("user-1", chunk.getCreatedBy());
        assertEquals("user-1", chunk.getUpdatedBy());
    }

    @Test
    void parseAndChunkMarksDocumentFailedWhenRunningLogCannotBeCreated() {
        KnowledgeDocumentDO document = document();
        when(knowledgeDocumentMapper.selectById("doc-1")).thenReturn(document);
        when(chunkLogMapper.selectLatestByDocId("doc-1")).thenReturn(null);
        doThrow(new RuntimeException("insert log failed")).when(chunkLogMapper).insert(any(KnowledgeDocumentChunkLogDO.class));

        org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class, () -> service.parseAndChunk("doc-1"));

        assertEquals("failed", document.getStatus());
        verify(knowledgeDocumentMapper).updateById(document);
    }

    private KnowledgeDocumentDO document() {
        KnowledgeDocumentDO document = new KnowledgeDocumentDO();
        document.setId("doc-1");
        document.setKbId("kb-1");
        document.setDocName("研发手册.md");
        document.setFileUrl("http://localhost:9000/devbrain/doc-1.md");
        document.setFileType("md");
        document.setProcessMode("chunk");
        document.setStatus("processing");
        document.setChunkStrategy("fixed_size");
        document.setChunkConfig("{\"chunkSize\":512,\"overlapSize\":128}");
        document.setPipelineId("pipe-1");
        document.setCreatedBy("user-1");
        document.setUpdatedBy("user-1");
        document.setDeleted(0);
        return document;
    }
}
