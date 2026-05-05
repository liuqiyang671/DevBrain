package edu.cqupt.devbrain.infra.embedding;

import edu.cqupt.devbrain.framework.exception.RemoteException;
import edu.cqupt.devbrain.infra.ai.embedding.EmbeddingService;
import edu.cqupt.devbrain.infra.config.AIModelProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;

import static edu.cqupt.devbrain.framework.errorcode.BaseErrorCode.REMOTE_ERROR;

/**
 * 路由型 Embedding 服务。
 * <p>
 * 默认调用按候选模型优先级自动降级；指定模型调用只使用对应候选项，避免跨模型维度或语义空间混用。
 */
@Slf4j
@Service
@Primary
public class RoutingEmbeddingService implements EmbeddingService {

    private final AIModelProperties properties;
    private final Map<String, EmbeddingClient> clients;

    /**
     * 构造路由型嵌入服务，注入模型配置和所有可用的嵌入客户端。
     * <p>
     * 客户端按 provider 名称建索引，运行时通过候选模型的 provider 字段进行路由。
     *
     * @param properties AI 模型配置属性
     * @param clients    所有已注册的嵌入客户端实现
     */
    public RoutingEmbeddingService(AIModelProperties properties, List<EmbeddingClient> clients) {
        this.properties = properties;
        this.clients = indexClients(clients);
    }

    @Override
    public List<Float> embed(String text) {
        return callWithFallback(candidate -> clientFor(candidate).embed(text, targetFor(candidate)));
    }

    @Override
    public List<Float> embed(String text, String modelId) {
        AIModelProperties.ModelCandidate candidate = candidateById(modelId)
                .orElseThrow(() -> new RemoteException("嵌入模型不可用：" + modelId));
        List<Float> embedding = clientFor(candidate).embed(text, targetFor(candidate));
        validateDimension(candidate, embedding);
        return embedding;
    }

    @Override
    public List<List<Float>> embedBatch(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            return List.of();
        }
        return callBatchWithFallback(candidate -> clientFor(candidate).embedBatch(texts, targetFor(candidate)));
    }

    @Override
    public List<List<Float>> embedBatch(List<String> texts, String modelId) {
        if (texts == null || texts.isEmpty()) {
            return List.of();
        }
        AIModelProperties.ModelCandidate candidate = candidateById(modelId)
                .orElseThrow(() -> new RemoteException("嵌入模型不可用：" + modelId));
        List<List<Float>> embeddings = clientFor(candidate).embedBatch(texts, targetFor(candidate));
        validateDimensions(candidate, embeddings);
        return embeddings;
    }

    private List<Float> callWithFallback(Function<AIModelProperties.ModelCandidate, List<Float>> operation) {
        List<AIModelProperties.ModelCandidate> candidates = fallbackCandidates();
        if (candidates.isEmpty()) {
            throw new RemoteException("默认嵌入模型不可用：" + defaultModelId());
        }

        RuntimeException lastFailure = null;
        for (AIModelProperties.ModelCandidate candidate : candidates) {
            try {
                List<Float> embedding = operation.apply(candidate);
                validateDimension(candidate, embedding);
                return embedding;
            } catch (RuntimeException ex) {
                lastFailure = ex;
                log.warn("Embedding 候选模型调用失败，candidateId={}，provider={}，model={}",
                        candidate.getId(), candidate.getProvider(), candidate.getModel(), ex);
            }
        }
        throw new RemoteException("所有默认嵌入模型调用失败：" + defaultModelId()
                + lastFailureMessage(lastFailure), lastFailure, REMOTE_ERROR);
    }

    private List<List<Float>> callBatchWithFallback(
            Function<AIModelProperties.ModelCandidate, List<List<Float>>> operation
    ) {
        List<AIModelProperties.ModelCandidate> candidates = fallbackCandidates();
        if (candidates.isEmpty()) {
            throw new RemoteException("默认嵌入模型不可用：" + defaultModelId());
        }

        RuntimeException lastFailure = null;
        for (AIModelProperties.ModelCandidate candidate : candidates) {
            try {
                List<List<Float>> embeddings = operation.apply(candidate);
                validateDimensions(candidate, embeddings);
                return embeddings;
            } catch (RuntimeException ex) {
                lastFailure = ex;
                log.warn("Embedding 候选模型批量调用失败，candidateId={}，provider={}，model={}",
                        candidate.getId(), candidate.getProvider(), candidate.getModel(), ex);
            }
        }
        throw new RemoteException("所有默认嵌入模型批量调用失败：" + defaultModelId()
                + lastFailureMessage(lastFailure), lastFailure, REMOTE_ERROR);
    }

    private List<AIModelProperties.ModelCandidate> fallbackCandidates() {
        String defaultModel = defaultModelId();
        List<AIModelProperties.ModelCandidate> sortedEnabled = candidates().stream()
                .filter(AIModelProperties.ModelCandidate::isEnabled)
                .sorted(Comparator.comparingInt(AIModelProperties.ModelCandidate::getPriority))
                .toList();
        Optional<AIModelProperties.ModelCandidate> defaultCandidate = sortedEnabled.stream()
                .filter(candidate -> defaultModel.equals(candidate.getId()))
                .findFirst();
        if (defaultCandidate.isEmpty()) {
            return List.of();
        }
        return Stream.concat(
                        Stream.of(defaultCandidate.get()),
                        sortedEnabled.stream().filter(candidate -> !defaultModel.equals(candidate.getId()))
                )
                .toList();
    }

    private Optional<AIModelProperties.ModelCandidate> candidateById(String modelId) {
        if (!StringUtils.hasText(modelId)) {
            return Optional.empty();
        }
        return candidates().stream()
                .filter(AIModelProperties.ModelCandidate::isEnabled)
                .filter(candidate -> modelId.equals(candidate.getId()) || modelId.equals(candidate.getModel()))
                .findFirst();
    }

    private List<AIModelProperties.ModelCandidate> candidates() {
        return Optional.ofNullable(properties.getEmbedding())
                .map(AIModelProperties.ModelGroup::getCandidates)
                .orElse(List.of());
    }

    private String defaultModelId() {
        return Optional.ofNullable(properties.getEmbedding())
                .map(AIModelProperties.ModelGroup::getDefaultModel)
                .filter(StringUtils::hasText)
                .orElse("");
    }

    private EmbeddingClient clientFor(AIModelProperties.ModelCandidate candidate) {
        EmbeddingClient client = clients.get(candidate.getProvider());
        if (client == null) {
            throw new RemoteException("嵌入模型客户端不存在：" + candidate.getProvider());
        }
        return client;
    }

    private ModelTarget targetFor(AIModelProperties.ModelCandidate candidate) {
        AIModelProperties.ProviderConfig provider = properties.getProviders().get(candidate.getProvider());
        return ModelTarget.from(candidate, provider);
    }

    private void validateDimensions(AIModelProperties.ModelCandidate candidate, List<List<Float>> embeddings) {
        if (embeddings == null) {
            throw new RemoteException("Embedding 返回结果不能为空：" + candidate.getId());
        }
        for (List<Float> embedding : embeddings) {
            validateDimension(candidate, embedding);
        }
    }

    private void validateDimension(AIModelProperties.ModelCandidate candidate, List<Float> embedding) {
        if (embedding == null) {
            throw new RemoteException("Embedding 返回向量不能为空：" + candidate.getId());
        }
        if (embedding.size() != candidate.getDimension()) {
            throw new RemoteException("Embedding 返回维度不匹配，candidateId=" + candidate.getId()
                    + "，expected=" + candidate.getDimension()
                    + "，actual=" + embedding.size());
        }
    }

    private Map<String, EmbeddingClient> indexClients(List<EmbeddingClient> clients) {
        Map<String, EmbeddingClient> indexed = new LinkedHashMap<>();
        if (clients == null) {
            return indexed;
        }
        for (EmbeddingClient client : clients) {
            indexed.put(client.provider(), client);
        }
        return indexed;
    }

    private String lastFailureMessage(RuntimeException failure) {
        return failure == null || !StringUtils.hasText(failure.getMessage())
                ? ""
                : "，lastError=" + failure.getMessage();
    }
}
