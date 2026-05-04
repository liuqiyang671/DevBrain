package edu.cqupt.devbrain.knowledge.mq.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 文档分块消息事件，承载异步解析所需的最小业务上下文。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DocumentChunkEvent implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 待解析文档 ID。
     */
    private String docId;

    /**
     * 触发解析的用户 ID。
     */
    private String userId;

    /**
     * 文档所属知识库 ID。
     */
    private String kbId;
}
