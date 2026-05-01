package edu.cqupt.devbrain.framework.convention;

/**
 * RAG 检索命中结果
 * <p>
 * 表示一次向量检索或相关性搜索命中的单条记录
 * 包含原始文档片段 主键以及相关性得分
 */
public class RetrievedChunk {

    /**
     * 命中记录的唯一标识
     * 比如向量库中的 primary key 或文档 id
     */
    private String id;

    /**
     * 命中的文本内容
     * 一般是被切分后的文档片段或段落
     */
    private String text;

    /**
     * 命中得分
     * 数值越大表示与查询的相关性越高
     */
    private Float score;

    public RetrievedChunk() {
    }

    public RetrievedChunk(String id, String text, Float score) {
        this.id = id;
        this.text = text;
        this.score = score;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public Float getScore() {
        return score;
    }

    public void setScore(Float score) {
        this.score = score;
    }

    /**
     * 创建 Builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder 模式
     */
    public static class Builder {
        private final RetrievedChunk chunk = new RetrievedChunk();

        public Builder id(String id) {
            chunk.setId(id);
            return this;
        }

        public Builder text(String text) {
            chunk.setText(text);
            return this;
        }

        public Builder score(Float score) {
            chunk.setScore(score);
            return this;
        }

        public RetrievedChunk build() {
            return chunk;
        }
    }
}
