package edu.cqupt.devbrain.rag.core.rewrite;

import edu.cqupt.devbrain.framework.convention.ChatMessage;

import java.util.List;

/**
 * 查询改写服务，负责把用户问题改写为更适合检索的查询。
 */
public interface QueryRewriteService {

    /**
     * 改写用户问题并拆分复合问题。
     *
     * @param userQuestion 用户原始问题
     * @param history      最近对话历史
     * @return 查询改写结果
     */
    RewriteResult rewriteWithSplit(String userQuestion, List<ChatMessage> history);
}
