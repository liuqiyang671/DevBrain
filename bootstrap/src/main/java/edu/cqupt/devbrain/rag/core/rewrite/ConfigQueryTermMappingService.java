package edu.cqupt.devbrain.rag.core.rewrite;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 基于配置的术语映射服务。
 */
@Service
@RequiredArgsConstructor
public class ConfigQueryTermMappingService implements QueryTermMappingService {

    private final QueryRewriteProperties properties;

    /**
     * 按术语长度降序遍历映射表，将别名替换为标准术语。
     * 英文别名使用单词边界正则匹配，中文别名直接字符串替换。
     */
    @Override
    public String normalize(String query) {
        if (!StringUtils.hasText(query) || properties.getTermMappings() == null
                || properties.getTermMappings().isEmpty()) {
            return query;
        }

        String normalized = query;
        // 按标准术语长度降序排列，优先匹配长术语，避免短术语误匹配
        // 例如 "Spring Boot" 应在 "Spring" 之前匹配
        List<Map.Entry<String, List<String>>> mappings = properties.getTermMappings()
                .entrySet()
                .stream()
                .sorted(Comparator.comparingInt((Map.Entry<String, List<String>> entry) -> entry.getKey().length())
                        .reversed())
                .toList();
        // 遍历每个标准术语的所有别名，逐个替换
        for (Map.Entry<String, List<String>> entry : mappings) {
            String standardTerm = entry.getKey();
            if (!StringUtils.hasText(standardTerm) || entry.getValue() == null) {
                continue;
            }
            for (String alias : entry.getValue()) {
                if (!StringUtils.hasText(alias)) {
                    continue;
                }
                normalized = replaceAlias(normalized, alias, standardTerm);
            }
        }
        return normalized;
    }

    private String replaceAlias(String input, String alias, String standardTerm) {
        if (isAsciiWord(alias)) {
            // 英文别名使用单词边界正则匹配，(?i) 忽略大小写
            // 负向前瞻/后顾确保不会匹配到单词中间（如 "ai" 不会匹配 "train" 中的 "ai"）
            Pattern pattern = Pattern.compile("(?i)(?<![A-Za-z0-9_])" + Pattern.quote(alias) + "(?![A-Za-z0-9_])");
            return pattern.matcher(input).replaceAll(standardTerm);
        }
        // 中文别名直接字符串替换
        return input.replace(alias, standardTerm);
    }

    private boolean isAsciiWord(String value) {
        return value.chars().allMatch(ch -> ch < 128)
                && value.toLowerCase(Locale.ROOT).chars()
                .anyMatch(ch -> ch >= 'a' && ch <= 'z');
    }
}
