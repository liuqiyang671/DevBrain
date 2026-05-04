package edu.cqupt.devbrain.knowledge.mq.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 知识库文档分块事件，承载异步分块任务所需的最小业务上下文。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeDocumentChunkEvent implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 待分块文档 ID。
     */
    private String docId;

    /**
     * 文档所属知识库 ID。
     */
    private String kbId;

    /**
     * 操作人用户 ID。
     */
    private String operator;
}
