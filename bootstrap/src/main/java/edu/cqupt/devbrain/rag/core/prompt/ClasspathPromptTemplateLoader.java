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

    @Override
    public String load(String path) {
        return templateCache.computeIfAbsent(path, this::loadTemplate);
    }

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

    private String loadTemplate(String path) {
        String normalized = normalize(path);
        ClassPathResource resource = new ClassPathResource(normalized);
        if (!resource.exists()) {
            throw new ServiceException("Prompt 模板不存在：" + path, BaseErrorCode.SERVICE_ERROR);
        }
        try {
            return new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new ServiceException("Prompt 模板读取失败：" + path, ex, BaseErrorCode.SERVICE_ERROR);
        }
    }

    private Map<String, String> parseSections(String path) {
        String content = load(path);
        Map<String, String> sections = new LinkedHashMap<>();
        String currentName = null;
        StringBuilder currentBody = new StringBuilder();
        for (String line : content.split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("--- section:") && trimmed.endsWith("---")) {
                if (currentName != null) {
                    sections.put(currentName, currentBody.toString().strip());
                }
                currentName = trimmed.substring("--- section:".length(), trimmed.length() - 3).trim();
                currentBody = new StringBuilder();
                continue;
            }
            if (currentName != null) {
                currentBody.append(line).append('\n');
            }
        }
        if (currentName != null) {
            sections.put(currentName, currentBody.toString().strip());
        }
        return sections;
    }

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

    private String normalize(String path) {
        if (!StringUtils.hasText(path)) {
            throw new ServiceException("Prompt 模板路径不能为空", BaseErrorCode.SERVICE_ERROR);
        }
        String cleanPath = path.startsWith("/") ? path.substring(1) : path;
        if (cleanPath.startsWith(PROMPT_ROOT)) {
            return cleanPath;
        }
        return PROMPT_ROOT + cleanPath;
    }
}
