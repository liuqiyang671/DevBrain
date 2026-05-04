package edu.cqupt.devbrain.sync.adapter;

import edu.cqupt.devbrain.framework.exception.ClientException;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class DocumentSourceAdapterRegistry {

    private final Map<String, DocumentSourceAdapter> adapterMap;

    public DocumentSourceAdapterRegistry(List<DocumentSourceAdapter> adapters) {
        this.adapterMap = adapters.stream()
                .collect(Collectors.toMap(DocumentSourceAdapter::sourceType, Function.identity()));
    }

    public DocumentSourceAdapter requireAdapter(String sourceType) {
        DocumentSourceAdapter adapter = adapterMap.get(sourceType);
        if (adapter == null) {
            throw new ClientException("不支持的文档来源类型: " + sourceType);
        }
        return adapter;
    }
}
