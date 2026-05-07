package edu.cqupt.devbrain.rag.core.prompt;

import java.util.Map;

/**
 * Prompt 模板加载器。
 */
public interface PromptTemplateLoader {

    /**
     * 加载指定路径的 Prompt 模板全文。
     */
    String load(String path);

    /**
     * 加载模板中的指定 section 并填充插槽变量。
     *
     * @param path    模板路径
     * @param section section 名称
     * @param slots   插槽变量映射
     * @return 渲染后的文本
     */
    String renderSection(String path, String section, Map<String, Object> slots);
}
