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

    @Override
    public String normalize(String query) {
        if (!StringUtils.hasText(query) || properties.getTermMappings() == null
                || properties.getTermMappings().isEmpty()) {
            return query;
        }

        String normalized = query;
        List<Map.Entry<String, List<String>>> mappings = properties.getTermMappings()
                .entrySet()
                .stream()
                .sorted(Comparator.comparingInt((Map.Entry<String, List<String>> entry) -> entry.getKey().length())
                        .reversed())
                .toList();
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
            Pattern pattern = Pattern.compile("(?i)(?<![A-Za-z0-9_])" + Pattern.quote(alias) + "(?![A-Za-z0-9_])");
            return pattern.matcher(input).replaceAll(standardTerm);
        }
        return input.replace(alias, standardTerm);
    }

    private boolean isAsciiWord(String value) {
        return value.chars().allMatch(ch -> ch < 128)
                && value.toLowerCase(Locale.ROOT).chars()
                .anyMatch(ch -> ch >= 'a' && ch <= 'z');
    }
}
