package edu.cqupt.devbrain.core.chunk;

import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 分块策略工厂，负责收集 Spring 容器中的所有分块策略并按模式快速查找。
 */
@Component
public class ChunkingStrategyFactory {

    /**
     * Spring 自动注入的分块策略实现列表。
     */
    private final List<ChunkingStrategy> strategies;

    /**
     * 使用 EnumMap 按 ChunkingMode 索引策略，适合枚举 key 的高效查找场景。
     */
    private final EnumMap<ChunkingMode, ChunkingStrategy> strategyMap = new EnumMap<>(ChunkingMode.class);

    /**
     * 构造函数注入所有 ChunkingStrategy 实现。
     *
     * @param strategies Spring 容器自动收集到的分块策略列表
     */
    public ChunkingStrategyFactory(List<ChunkingStrategy> strategies) {
        this.strategies = strategies;
    }

    /**
     * 初始化策略索引，并检测同一 ChunkingMode 是否被多个策略重复注册。
     */
    @PostConstruct
    public void init() {
        for (ChunkingStrategy strategy : strategies) {
            ChunkingMode mode = strategy.getType();
            if (strategyMap.put(mode, strategy) != null) {
                throw new IllegalStateException("重复注册分块模式: " + mode);
            }
        }
    }

    /**
     * 获取指定分块模式对应的策略，未注册时返回空 Optional。
     *
     * @param mode 分块模式
     * @return 对应的分块策略（可能为空）
     */
    public Optional<ChunkingStrategy> findStrategy(ChunkingMode mode) {
        return Optional.ofNullable(strategyMap.get(mode));
    }

    /**
     * 获取指定分块模式对应的策略，未注册时抛出异常。
     *
     * @param mode 分块模式
     * @return 对应的分块策略
     * @throws IllegalArgumentException 当前模式没有可用策略时抛出
     */
    public ChunkingStrategy requireStrategy(ChunkingMode mode) {
        ChunkingStrategy strategy = strategyMap.get(mode);
        if (strategy == null) {
            throw new IllegalArgumentException("不支持的分块模式: " + mode);
        }
        return strategy;
    }

    /**
     * 返回所有已注册的分块模式集合。
     *
     * @return 已注册的 ChunkingMode 集合
     */
    public Set<ChunkingMode> getAvailableModes() {
        return Set.copyOf(strategyMap.keySet());
    }
}
