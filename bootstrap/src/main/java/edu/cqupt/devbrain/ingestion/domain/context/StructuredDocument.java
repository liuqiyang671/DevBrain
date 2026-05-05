package edu.cqupt.devbrain.ingestion.domain.context;

import lombok.Builder;
import lombok.Data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 解析后的结构化文档，保留全文、章节、表格和扩展元数据。
 */
@Data
@Builder
public class StructuredDocument {

    /**
     * 文档全文文本。
     */
    private String text;

    /**
     * 章节列表，通常由 Markdown 标题、Word 标题或 PDF 结构提取而来。
     */
    @Builder.Default
    private List<Section> sections = new ArrayList<>();

    /**
     * 表格块列表，用于后续表格感知分块和元数据增强。
     */
    @Builder.Default
    private List<TableBlock> tables = new ArrayList<>();

    /**
     * 文档级元数据。
     */
    @Builder.Default
    private Map<String, Object> metadata = new HashMap<>();

    /**
     * 文档章节块。
     */
    @Data
    @Builder
    public static class Section {

        /**
         * 章节标题。
         */
        private String title;

        /**
         * 标题层级，一级标题为 1。
         */
        private int level;

        /**
         * 章节正文。
         */
        private String content;

        /**
         * 章节在全文中的起始偏移。
         */
        private int startOffset;

        /**
         * 章节在全文中的结束偏移。
         */
        private int endOffset;
    }

    /**
     * 文档表格块。
     */
    @Data
    @Builder
    public static class TableBlock {

        /**
         * 表格标题或最近的章节标题。
         */
        private String title;

        /**
         * 表格行数据，第一行通常可作为表头。
         */
        @Builder.Default
        private List<List<String>> rows = new ArrayList<>();

        /**
         * 表格在全文中的起始偏移。
         */
        private int startOffset;

        /**
         * 表格在全文中的结束偏移。
         */
        private int endOffset;
    }
}
