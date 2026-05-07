package edu.cqupt.devbrain.rag.core.prompt;

import edu.cqupt.devbrain.framework.errorcode.BaseErrorCode;
import edu.cqupt.devbrain.framework.exception.ServiceException;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 基于 classpath 的 Prompt 模板加载器。
 */
@Component
public class ClasspathPromptTemplateLoader implements PromptTemplateLoader {

    private static final String PROMPT_ROOT = "rag/prompt/";

    private final Map<String, String> templateCache = new ConcurrentHashMap<>();
    private final Map<String, Map<String, String>> sectionCache = new ConcurrentHashMap<>();

    /**
     * 加载模板全文，带 ConcurrentHashMap 缓存。
     */
    @Override
    public String load(String path) {
        return templateCache.computeIfAbsent(path, this::loadTemplate);
    }

    /**
     * 从模板中解析指定 section，填充插槽变量后返回渲染结果。
     * section 通过 "--- section:xxx ---" 分隔符标记。
     */
    @Override
    public String renderSection(String path, String section, Map<String, Object> slots) {
        Map<String, String> sections = sectionCache.computeIfAbsent(path, this::parseSections);
        String template = sections.get(section);
        if (template == null) {
            throw new ServiceException("Prompt 模板 section 不存在：" + path + "#" + section,
                    BaseErrorCode.SERVICE_ERROR);
        }
        return render(template, slots);
    }

    /**
     * 从 classpath 加载模板文件全文，路径不存在时抛出业务异常。
     */
    private String loadTemplate(String path) {
        // 规范化路径，确保以 rag/prompt/ 为前缀
        String normalized = normalize(path);
        ClassPathResource resource = new ClassPathResource(normalized);
        if (!resource.exists()) {
            throw new ServiceException("Prompt 模板不存在：" + path, BaseErrorCode.SERVICE_ERROR);
        }
        try {
            // 一次性读取全部字节并按 UTF-8 解码为字符串
            return new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new ServiceException("Prompt 模板读取失败：" + path, ex, BaseErrorCode.SERVICE_ERROR);
        }
    }

    /**
     * 按 "--- section:xxx ---" 分隔符将模板拆分为多个 section。
     * 每个 section 的名称由分隔符中的 xxx 部分决定。
     */
    private Map<String, String> parseSections(String path) {
        String content = load(path);
        Map<String, String> sections = new LinkedHashMap<>();
        String currentName = null;
        StringBuilder currentBody = new StringBuilder();
        for (String line : content.split("\\R")) {
            String trimmed = line.trim();
            // 遇到 section 分隔符时，保存上一个 section 并开始新的
            if (trimmed.startsWith("--- section:") && trimmed.endsWith("---")) {
                if (currentName != null) {
                    sections.put(currentName, currentBody.toString().strip());
                }
                currentName = trimmed.substring("--- section:".length(), trimmed.length() - 3).trim();
                currentBody = new StringBuilder();
                continue;
            }
            // 非分隔符行追加到当前 section body
            if (currentName != null) {
                currentBody.append(line).append('\n');
            }
        }
        // 保存最后一个 section
        if (currentName != null) {
            sections.put(currentName, currentBody.toString().strip());
        }
        return sections;
    }

    /**
     * 将模板中的 {key} 占位符替换为 slots 中对应的值。
     */
    private String render(String template, Map<String, Object> slots) {
        String result = template;
        if (slots == null || slots.isEmpty()) {
            return result;
        }
        for (Map.Entry<String, Object> entry : slots.entrySet()) {
            result = result.replace("{" + entry.getKey() + "}", String.valueOf(entry.getValue()));
        }
        return result;
    }

    /**
     * 规范化模板路径：去除前导斜杠，补全 rag/prompt/ 前缀。
     */
    private String normalize(String path) {
        if (!StringUtils.hasText(path)) {
            throw new ServiceException("Prompt 模板路径不能为空", BaseErrorCode.SERVICE_ERROR);
        }
        // 去除前导斜杠，避免 ClassPathResource 解析异常
        String cleanPath = path.startsWith("/") ? path.substring(1) : path;
        // 已包含前缀则直接返回，否则补全
        if (cleanPath.startsWith(PROMPT_ROOT)) {
            return cleanPath;
        }
        return PROMPT_ROOT + cleanPath;
    }
}
