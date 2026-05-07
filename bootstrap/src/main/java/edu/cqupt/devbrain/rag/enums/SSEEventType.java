package edu.cqupt.devbrain.rag.enums;

import lombok.Getter;

/**
 * RAG SSE 事件类型。
 */
@Getter
public enum SSEEventType {

    META("meta"),

    MESSAGE("message"),

    FINISH("finish"),

    DONE("done"),

    CANCEL("cancel"),

    REJECT("reject"),

    ERROR("error");

    private final String value;

    SSEEventType(String value) {
        this.value = value;
    }
}
