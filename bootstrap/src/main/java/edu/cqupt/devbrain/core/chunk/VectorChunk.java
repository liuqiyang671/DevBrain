package edu.cqupt.devbrain.core.chunk;

import cn.hutool.core.util.IdUtil;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.util.HashMap;
import java.util.Map;

/**
 * 文档分块后的基础数据单元，用于后续向量嵌入、向量存储和 RAG 检索。
 */
@Data
public class VectorChunk {

    /**
     * Chunk 唯一标识，使用雪花算法生成。
     */
    private String chunkId;

    /**
     * 当前分块在原始文档中的顺序索引，从 0 开始。
     */
    private Integer index;

    /**
     * 当前分块的文本内容，会作为向量嵌入的输入文本。
     */
    private String content;

    /**
     * 分块元数据，后续可扩展关键词、摘要、来源、标题层级等信息。
     */
    private Map<String, Object> metadata;

    /**
     * 当前分块的嵌入向量，向量数据单独存储，不参与 JSON 序列化。
     */
    @JsonIgnore
    private float[] embedding;

    /**
     * 创建只包含文本和索引的分块对象，并自动生成雪花 ID 与空元数据。
     *
     * @param content 分块文本内容
     * @param index 分块顺序索引
     * @return 初始化完成的向量分块对象
     */
    public static VectorChunk of(String content, int index) {
        VectorChunk chunk = new VectorChunk();
        chunk.setChunkId(IdUtil.getSnowflakeNextIdStr());
        chunk.setIndex(index);
        chunk.setContent(content);
        chunk.setMetadata(new HashMap<>());
        return chunk;
    }
}
