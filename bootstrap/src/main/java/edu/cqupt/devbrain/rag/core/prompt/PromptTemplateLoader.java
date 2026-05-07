package edu.cqupt.devbrain.rag.core.prompt;

import java.util.Map;

/**
 * Prompt 模板加载器。
 */
public interface PromptTemplateLoader {

    String load(String path);

    String renderSection(String path, String section, Map<String, Object> slots);
}
