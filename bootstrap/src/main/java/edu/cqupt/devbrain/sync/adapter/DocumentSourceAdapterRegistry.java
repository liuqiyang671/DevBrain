package edu.cqupt.devbrain.sync.adapter;

import edu.cqupt.devbrain.framework.exception.ClientException;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 文档来源适配器注册中心，根据来源类型查找对应的适配器实例。
 */
@Component
public class DocumentSourceAdapterRegistry {

    private final Map<String, DocumentSourceAdapter> adapterMap;

    /**
     * 构造方法，自动注入所有 {@link DocumentSourceAdapter} 实现并建立类型映射。
     */
    public DocumentSourceAdapterRegistry(List<DocumentSourceAdapter> adapters) {
        this.adapterMap = adapters.stream()
                .collect(Collectors.toMap(DocumentSourceAdapter::sourceType, Function.identity()));
    }

    /**
     * 根据来源类型获取适配器，不存在时抛出异常。
     *
     * @param sourceType 来源类型标识
     * @return 对应的适配器实例
     */
    public DocumentSourceAdapter requireAdapter(String sourceType) {
        DocumentSourceAdapter adapter = adapterMap.get(sourceType);
        if (adapter == null) {
            throw new ClientException("不支持的文档来源类型: " + sourceType);
        }
        return adapter;
    }
}
