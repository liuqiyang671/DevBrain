package edu.cqupt.devbrain.ingestion.node;

import edu.cqupt.devbrain.ingestion.domain.SourceType;
import edu.cqupt.devbrain.ingestion.domain.context.DocumentSource;
import edu.cqupt.devbrain.ingestion.domain.context.IngestionContext;
import edu.cqupt.devbrain.ingestion.domain.pipeline.NodeConfig;
import edu.cqupt.devbrain.ingestion.domain.result.NodeResult;
import edu.cqupt.devbrain.ingestion.node.fetcher.DocumentFetcher;
import jakarta.annotation.PostConstruct;
import org.apache.tika.Tika;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 摄入流水线 Fetcher 节点，负责从 DocumentSource 拉取原始文档字节并写入上下文。
 */
@Component
public class FetcherNode implements IngestionNode {

    /**
     * 节点类型标识，需要与 PipelineDefinition 中的 nodeType 保持一致。
     */
    public static final String NODE_TYPE = "fetcher";

    /**
     * Tika 仅用于轻量 MIME 检测，不承担正文解析。
     */
    private static final Tika TIKA = new Tika();

    /**
     * Spring 自动注入的所有来源获取策略。
     */
    private final List<DocumentFetcher> fetchers;

    /**
     * 按来源类型索引的获取策略表。
     */
    private final Map<SourceType, DocumentFetcher> fetcherRegistry = new EnumMap<>(SourceType.class);

    /**
     * 构造函数注入获取策略列表。
     *
     * @param fetchers 获取策略列表
     */
    public FetcherNode(List<DocumentFetcher> fetchers) {
        this.fetchers = fetchers == null ? List.of() : fetchers;
    }

    /**
     * 初始化来源类型索引，重复来源类型视为配置错误。
     */
    @PostConstruct
    public void init() {
        fetcherRegistry.clear();
        for (DocumentFetcher fetcher : fetchers) {
            DocumentFetcher previous = fetcherRegistry.put(fetcher.getSupportedType(), fetcher);
            if (previous != null) {
                throw new IllegalStateException("重复注册文档获取策略: " + fetcher.getSupportedType());
            }
        }
    }

    /**
     * 返回 Fetcher 节点类型标识。
     */
    @Override
    public String getNodeType() {
        return NODE_TYPE;
    }

    /**
     * 执行文档获取逻辑；若上下文已有 rawBytes，则按幂等语义直接跳过。
     */
    @Override
    public NodeResult execute(IngestionContext context, NodeConfig config) {
        if (context.getRawBytes() != null && context.getRawBytes().length > 0) {
            return NodeResult.ok("原始内容已存在，跳过获取");
        }
        DocumentSource source = context.getSource();
        if (source == null || source.getType() == null) {
            return NodeResult.fail("文档来源不能为空");
        }

        DocumentFetcher fetcher = fetcherRegistry.get(source.getType());
        if (fetcher == null) {
            return NodeResult.fail("暂未支持的文档来源: " + source.getType());
        }

        try {
            byte[] rawBytes = fetcher.fetch(source);
            if (rawBytes == null) {
                return NodeResult.fail("获取结果为空");
            }
            context.setRawBytes(rawBytes);
            fillMimeTypeIfAbsent(context, source, rawBytes);
            return NodeResult.ok("文档获取成功");
        } catch (Exception ex) {
            String error = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
            return NodeResult.fail(error);
        }
    }

    /**
     * MIME 类型为空时基于文件名和字节内容进行检测，检测失败不阻断流水线。
     */
    private void fillMimeTypeIfAbsent(IngestionContext context, DocumentSource source, byte[] rawBytes) {
        if (StringUtils.hasText(context.getMimeType())) {
            return;
        }
        try {
            String resourceName = StringUtils.hasText(source.getFileName()) ? source.getFileName() : source.getLocation();
            context.setMimeType(TIKA.detect(rawBytes, resourceName));
        } catch (Exception ignored) {
            // MIME 检测失败时交给 ParserNode 和解析器兜底处理。
        }
    }
}
